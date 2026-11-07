[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Context = 'kind-docflow-staging',
    [string]$Namespace = 'docflow-staging',
    [string]$Pod = 'postgres-0',
    [string]$DatabaseUser = 'docflow',
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][string]$TargetDatabase,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'
$kubectl = (Get-Command kubectl -ErrorAction Stop).Source
$resolvedInput = [System.IO.Path]::GetFullPath($InputPath)
if (-not (Test-Path -LiteralPath $resolvedInput -PathType Leaf)) {
    throw "Backup file not found: $resolvedInput"
}
if (-not $ConfirmRestore) {
    throw 'Restore requires -ConfirmRestore and must target a separate database'
}
if ($TargetDatabase -in @('docflow', 'postgres', 'template0', 'template1')) {
    throw "Refusing restore into protected database: $TargetDatabase"
}

if ($PSCmdlet.ShouldProcess($TargetDatabase, 'Restore PostgreSQL backup into isolated database')) {
    & $kubectl '--context' $Context '--namespace' $Namespace 'exec' $Pod '--' 'createdb' '-U' $DatabaseUser $TargetDatabase
    if ($LASTEXITCODE -ne 0) { throw "createdb failed with exit code $LASTEXITCODE" }
    Get-Content -LiteralPath $resolvedInput -AsByteStream |
        & $kubectl '--context' $Context '--namespace' $Namespace 'exec' '--stdin' $Pod '--' 'pg_restore' '-U' $DatabaseUser '-d' $TargetDatabase '--exit-on-error'
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE" }
    Write-Host "Restore completed in isolated database: $TargetDatabase"
}
