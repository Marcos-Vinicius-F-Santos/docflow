[CmdletBinding()]
param(
    [string]$Context = 'kind-docflow-staging',
    [string]$Namespace = 'docflow-staging',
    [string]$Pod = 'postgres-0',
    [string]$Database = 'docflow',
    [string]$DatabaseUser = 'docflow',
    [Parameter(Mandatory)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$kubectl = (Get-Command kubectl -ErrorAction Stop).Source
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Refusing to overwrite existing backup: $resolvedOutput"
}

$stderrPath = [System.IO.Path]::GetTempFileName()
$process = Start-Process -FilePath $kubectl -ArgumentList @(
    '--context', $Context,
    '--namespace', $Namespace,
    'exec', $Pod, '--',
    'pg_dump', '-Fc', '-U', $DatabaseUser, '-d', $Database
) -RedirectStandardOutput $resolvedOutput -RedirectStandardError $stderrPath -NoNewWindow -Wait -PassThru
if ($process.ExitCode -ne 0) {
    $errorText = Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $resolvedOutput -Force -ErrorAction SilentlyContinue
    throw "pg_dump failed with exit code $($process.ExitCode): $errorText"
}
Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
if ((Get-Item -LiteralPath $resolvedOutput).Length -le 0) {
    Remove-Item -LiteralPath $resolvedOutput -Force
    throw 'Backup file is empty'
}
Write-Host "Backup created: $resolvedOutput"
