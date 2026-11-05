param(
    [string]$EnvFile = '.env.local'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnvFile = Join-Path $projectRoot $EnvFile

function Import-EnvironmentFile([string]$path) {
    if (Test-Path -LiteralPath $path) {
        Get-Content -LiteralPath $path | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
                $value = $matches[2].Trim()
                if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                [Environment]::SetEnvironmentVariable($matches[1], $value)
            }
        }
    }
}

if (-not (Test-Path -LiteralPath $resolvedEnvFile)) {
    throw "Neon environment file not found: $resolvedEnvFile"
}

Import-EnvironmentFile (Join-Path $projectRoot 'infra/.env')
Import-EnvironmentFile $resolvedEnvFile

$connectionString = (& neon connection-string production --ssl require 2>$null | Select-Object -Last 1).Trim()
if (-not $connectionString.StartsWith('postgresql://')) {
    $connectionString = $env:DATABASE_URL
}
if (-not $connectionString) {
    throw 'A Neon PostgreSQL connection string is required'
}

$databaseUri = [Uri]($connectionString -replace '^postgres(ql)?://', 'https://')
$userInfo = $databaseUri.UserInfo.Split(':', 2)
$databaseUser = [Uri]::UnescapeDataString($userInfo[0])
$databasePassword = if ($userInfo.Length -gt 1) {
    [Uri]::UnescapeDataString($userInfo[1])
} else {
    ''
}
$databaseName = $databaseUri.AbsolutePath.TrimStart('/')
$databasePort = if ($connectionString -match '^[^:]+://[^/]+:(\d+)(?:/|$)') {
    [int]$matches[1]
} else {
    5432
}
$jdbcQuery = $databaseUri.Query -replace '(^|\?)channel_binding=require&?', '$1'
$jdbcQuery = $jdbcQuery.TrimEnd('&', '?')

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($databaseUri.Host):$databasePort/$databaseName$jdbcQuery"
$env:SPRING_DATASOURCE_USERNAME = $databaseUser
$env:SPRING_DATASOURCE_PASSWORD = $databasePassword

Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -B spring-boot:run
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
