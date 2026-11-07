[CmdletBinding()]
param(
    [string]$Context = 'kind-docflow-staging',
    [string]$Namespace = 'docflow-staging',
    [string]$ImageTag = 'k8s-local'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$kubectl = (Get-Command kubectl -ErrorAction Stop).Source
$kind = (Get-Command kind -ErrorAction Stop).Source

function Invoke-Kubectl {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $commandArguments = @('--context', $Context) + $Arguments
    & $kubectl @commandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

$envFile = Join-Path $projectRoot 'infra\.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Missing $envFile. Copy infra/.env.example to infra/.env and fill local values."
}

$values = @{}
Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*[^#=]+=' } | ForEach-Object {
    $pair = $_ -split '=', 2
    $values[$pair[0].Trim()] = $pair[1]
}

$requiredKeys = @(
    'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD',
    'RABBITMQ_USER', 'RABBITMQ_PASSWORD',
    'MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD',
    'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY'
)
foreach ($key in $requiredKeys) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
        throw "Missing non-empty $key in $envFile"
    }
}

$backendImage = "docflow-backend:$ImageTag"
$bootstrapImage = "docflow-bootstrap:$ImageTag"
$minioImage = 'minio:docflow-local'
$minioArchive = Join-Path $env:TEMP 'docflow-minio-kind.tar'
$metricsServerManifest = 'https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.1/components.yaml'

Push-Location $projectRoot
try {
    Write-Host 'Building backend image...'
    Invoke-Docker @('build', '--tag', $backendImage, '--file', 'backend/Dockerfile', 'backend')

    Write-Host 'Building bootstrap image...'
    Invoke-Docker @('build', '--tag', $bootstrapImage, '--file', 'infra/bootstrap/Dockerfile', 'infra/bootstrap')

    Write-Host 'Preparing MinIO image for kind...'
    Invoke-Docker @('image', 'inspect', 'quay.io/minio/minio:latest', '--format', '{{.Id}}')
    Invoke-Docker @('tag', 'quay.io/minio/minio:latest', $minioImage)

    Write-Host 'Loading images into kind...'
    & $kind 'load' 'docker-image' $backendImage '--name' ($Context -replace '^kind-', '')
    if ($LASTEXITCODE -ne 0) { throw "kind failed to load $backendImage" }
    & $kind 'load' 'docker-image' $bootstrapImage '--name' ($Context -replace '^kind-', '')
    if ($LASTEXITCODE -ne 0) { throw "kind failed to load $bootstrapImage" }
    Invoke-Docker @('image', 'save', '--platform', 'linux/amd64', $minioImage, '--output', $minioArchive)
    & $kind 'load' 'image-archive' $minioArchive '--name' ($Context -replace '^kind-', '')
    if ($LASTEXITCODE -ne 0) { throw "kind failed to load $minioImage" }
    Remove-Item -LiteralPath $minioArchive -Force -ErrorAction SilentlyContinue

    Write-Host 'Creating namespace...'
    Invoke-Kubectl @('apply', '--filename', 'infra/kubernetes/local/namespace.yaml')

    Write-Host 'Installing Metrics Server for local resource validation...'
    Invoke-Kubectl @('apply', '--filename', $metricsServerManifest)
    $metricsPatch = '{"spec":{"template":{"spec":{"containers":[{"name":"metrics-server","args":["--cert-dir=/tmp","--secure-port=10250","--kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname","--kubelet-use-node-status-port","--metric-resolution=15s","--kubelet-insecure-tls"]}]}}}}'
    Invoke-Kubectl @('patch', 'deployment/metrics-server', '--namespace', 'kube-system', '--type', 'strategic', '--patch', $metricsPatch)
    Invoke-Kubectl @('rollout', 'status', 'deployment/metrics-server', '--namespace', 'kube-system', '--timeout=180s')

    $secretArguments = @('create', 'secret', 'generic', 'docflow-secrets', '--namespace', $Namespace)
    foreach ($key in @(
        'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD',
        'RABBITMQ_USER', 'RABBITMQ_PASSWORD',
        'MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD',
        'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY',
        'WEB3FORMS_ACCESS_KEY', 'DOCFLOW_NOTIFICATION_DEFAULT_EMAIL'
    )) {
        $value = if ($values.ContainsKey($key)) { $values[$key] } else { '' }
        $secretArguments += "--from-literal=$key=$value"
    }
    $secretArguments += @('--dry-run=client', '--output', 'yaml')
    $secretYaml = & $kubectl @('--context', $Context) @secretArguments
    if ($LASTEXITCODE -ne 0) { throw 'kubectl could not render docflow-secrets' }
    $secretYaml | & $kubectl @('--context', $Context, 'apply', '--filename', '-')
    if ($LASTEXITCODE -ne 0) { throw 'kubectl could not apply docflow-secrets' }

    Write-Host 'Applying PostgreSQL, RabbitMQ and MinIO...'
    Invoke-Kubectl @('apply', '--filename', 'infra/kubernetes/local/platform.yaml')
    Invoke-Kubectl @('rollout', 'status', 'statefulset/postgres', '--namespace', $Namespace, '--timeout=180s')
    Invoke-Kubectl @('rollout', 'status', 'statefulset/rabbitmq', '--namespace', $Namespace, '--timeout=180s')
    Invoke-Kubectl @('rollout', 'status', 'deployment/minio', '--namespace', $Namespace, '--timeout=180s')

    Write-Host 'Running bootstrap job...'
    Invoke-Kubectl @('delete', 'job/docflow-bootstrap', '--namespace', $Namespace, '--ignore-not-found=true')
    Invoke-Kubectl @('apply', '--filename', 'infra/kubernetes/local/bootstrap.yaml')
    Invoke-Kubectl @('wait', '--for=condition=complete', 'job/docflow-bootstrap', '--namespace', $Namespace, '--timeout=180s')

    Write-Host 'Applying backend...'
    Invoke-Kubectl @('apply', '--filename', 'infra/kubernetes/local/application.yaml')
    Invoke-Kubectl @('rollout', 'status', 'deployment/docflow-backend', '--namespace', $Namespace, '--timeout=180s')
    Invoke-Kubectl @('get', 'pods,services', '--namespace', $Namespace)
}
finally {
    Pop-Location
}
