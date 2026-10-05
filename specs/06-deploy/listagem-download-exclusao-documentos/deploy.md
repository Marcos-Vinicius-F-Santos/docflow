# Procedimento de Deploy — Listagem, download e exclusão de documentos

Ambiente-alvo: Windows, checkout em E:\Projects\docflow
Infraestrutura: PostgreSQL, RabbitMQ e MinIO em Docker Compose
Aplicação: JAR Spring Boot em Java 21 e frontend Angular local
Data: 2026-09-21
Status: deploy local executado após aprovação; nenhum push/publicação externa executado.

## Registro desta execução

- Backend iniciado com o JAR gerado pela revisão, PID 51544, health UP.
- Frontend iniciado com PID 54292 a partir de uma cópia temporária do mesmo código:
  C:\Users\marco\AppData\Local\Temp\docflow-frontend-deploy-a0b232541bad4399b127a0601e641055.
  A cópia foi necessária porque o node_modules do checkout estava com
  node.napi.node/esbuild.exe bloqueado por outro processo; o checkout não teve
  código-fonte alterado por essa contingência.
- Frontend respondeu HTTP 200 em /documents; as rotas /documents/new e o detalhe de
  um documento COMPLETED foram verificadas no navegador.
- Smoke real passou com documentId
  c3970166-eff3-49a2-b478-a9088335e7ed: PENDING → COMPLETED, download de 4.289 bytes,
  DELETE 204 e item ausente após a exclusão.
- Testes backend/Testcontainers e frontend passaram; o bundle foi gerado sem
  referências proibidas de infraestrutura ou segredos.
- RabbitMQ ficou com um consumidor na fila principal, prefetch 1 e filas de retry/DLQ
  vazias.
- Nenhum commit, push, migration, purge, remoção de volume ou limpeza ampla foi feito.

Este repositório não possui hosting externo, imagem Docker do backend, reverse proxy
de produção ou pipeline CI/CD. Neste projeto, o procedimento reproduz o ambiente
operacional local e, opcionalmente, publica o estado aprovado no GitHub. O push não
coloca uma aplicação executável na internet.

## Pré-condições

- [x] A revisão de convergência foi concluída em
      specs/05-verificacao/listagem-download-exclusao-documentos/checklist-convergencia.md.
- [ ] O diff final e o commit/ref exato foram revisados.
- [ ] O checkout usado no deploy está limpo, ou todas as alterações presentes foram
      explicitamente revisadas.
- [ ] E:\Projects\docflow\infra\.env existe, não está versionado e contém os valores
      do ambiente.
- [ ] Docker Desktop está ativo.
- [ ] Java 21 está disponível no PATH.
- [ ] Node.js 24.19.0 e npm 12.0.2 estão disponíveis no PATH.
- [ ] O JAR anterior aprovado existe em
      E:\Projects\docflow\backend\rollback\dockflow-previous.jar.
- [ ] O ref anterior do frontend foi registrado antes de substituir o processo atual.
- [ ] O aprovador autorizou iniciar o deploy.

Se algum item obrigatório permanecer desmarcado, pare. Não use remoção de volumes,
alteração manual no banco ou purge de filas para contornar uma pré-condição.

## Variáveis de ambiente e segredos

A feature não cria novas variáveis nem exige migration. Ela usa as integrações já
existentes. Em ambiente novo, confirme estas variáveis em infra/.env; substitua os
valores de exemplo por segredos reais e não os imprima no terminal:

| Grupo | Variáveis |
|---|---|
| PostgreSQL | POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD |
| RabbitMQ | RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASSWORD, RABBITMQ_MANAGEMENT_PORT |
| MinIO administrativo | MINIO_ROOT_USER, MINIO_ROOT_PASSWORD |
| MinIO da aplicação | MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_STAGING_BUCKET, MINIO_BUCKET |
| Política do processamento | DOCFLOW_MESSAGING_ENABLED=true, DOCFLOW_RECONCILIATION_ENABLED=true, DOCFLOW_RECONCILIATION_MAX_ATTEMPTS=5, DOCFLOW_RECONCILIATION_BACKOFF=PT1M,PT5M,PT15M,PT30M,PT60M, DOCFLOW_RECONCILIATION_SCHEDULE_INTERVAL=PT1M, DOCFLOW_PROCESSING_TIMEOUT=PT3S |

Confirme também:

- MINIO_STAGING_BUCKET e MINIO_BUCKET são diferentes;
- os buckets são os já usados pelo ambiente, sem criar buckets alternativos;
- as credenciais da aplicação MinIO não são as credenciais root por conveniência;
- o frontend não recebe segredo: environment.ts usa /documents e
  environment.development.ts usa /api/documents com proxy para localhost:8080;
- não há mudança de variável específica desta feature.

Carregue o arquivo somente no processo atual do PowerShell:

~~~powershell
Set-Location E:\Projects\docflow
$envLines = Get-Content .\infra\.env |
  Where-Object { $_ -and -not $_.Trim().StartsWith('#') }
foreach ($envLine in $envLines) {
  $name, $value = $envLine -split '=', 2
  Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
~~~

Valide nomes e não valores:

~~~powershell
$requiredNames = @(
  'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD',
  'RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USER', 'RABBITMQ_PASSWORD',
  'MINIO_ENDPOINT', 'MINIO_ACCESS_KEY', 'MINIO_SECRET_KEY',
  'MINIO_STAGING_BUCKET', 'MINIO_BUCKET'
)
foreach ($requiredName in $requiredNames) {
  $requiredValue = (Get-Item "Env:$requiredName" -ErrorAction SilentlyContinue).Value
  if ([string]::IsNullOrWhiteSpace($requiredValue)) {
    throw "Variável obrigatória ausente: $requiredName"
  }
}
if ($env:MINIO_STAGING_BUCKET -eq $env:MINIO_BUCKET) {
  throw 'Os buckets de staging e final precisam ser diferentes'
}
~~~

## Passos do deploy

### 1. Congelar e identificar a entrega

~~~powershell
Set-Location E:\Projects\docflow
git status --short
git diff --check
$deployRef = git rev-parse HEAD
$previousRef = git rev-parse HEAD^
git log -1 --oneline
Write-Output "Deploy ref: $deployRef"
Write-Output "Rollback ref: $previousRef"
~~~

Pare se houver alteração não revisada, falha no diff ou se o ref anterior não puder
ser identificado. Não inclua backend/dockflow.pid, backend/rollback ou logs locais
no artefato publicado.

Preserve o hash dos artefatos antes da troca:

~~~powershell
Get-FileHash .\backend\rollback\dockflow-previous.jar -Algorithm SHA256
if (Test-Path .\backend\target\dockflow-0.0.1-SNAPSHOT.jar) {
  Get-FileHash .\backend\target\dockflow-0.0.1-SNAPSHOT.jar -Algorithm SHA256
}
~~~

### 2. Subir e validar a infraestrutura

~~~powershell
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres rabbitmq minio
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
~~~

Valide a saúde antes de iniciar a aplicação:

~~~powershell
Invoke-WebRequest http://localhost:9000/minio/health/live -UseBasicParsing
Test-NetConnection localhost -Port 5432
Test-NetConnection localhost -Port 5672
~~~

Se falhar, consulte:

~~~powershell
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres rabbitmq minio bootstrap
~~~

Não execute docker compose down -v. Os volumes postgres-data, minio_data e
rabbitmq_data contêm dados do ambiente.

### 3. Testar e empacotar o backend

~~~powershell
Set-Location E:\Projects\docflow\backend
.\mvnw.cmd -q clean verify
~~~

O resultado esperado é a suíte completa verde, sem falhas ou erros. Em seguida,
confirme o JAR:

~~~powershell
if (-not (Test-Path .\target\dockflow-0.0.1-SNAPSHOT.jar)) {
  throw 'JAR do backend não foi gerado'
}
Get-FileHash .\target\dockflow-0.0.1-SNAPSHOT.jar -Algorithm SHA256
~~~

Não há migration nova nesta feature. O Spring Boot deve apenas validar o schema
existente com Hibernate e aplicar migrations pendentes já versionadas pelo Flyway.
Não execute SQL manual.

### 4. Trocar o processo do backend

Use um diretório de runtime fora do repositório:

~~~powershell
$runtimeDir = Join-Path $env:TEMP 'docflow-listagem-deploy'
New-Item -ItemType Directory -Force $runtimeDir | Out-Null
$backendOut = Join-Path $runtimeDir 'backend.out.log'
$backendErr = Join-Path $runtimeDir 'backend.err.log'
$backendPidFile = Join-Path $runtimeDir 'backend.pid'
~~~

Se houver processo anterior registrado, valide que o PID é um JAR do DocFlow antes
de pará-lo. Não mate um processo desconhecido que use a porta 8080:

~~~powershell
if (Test-Path $backendPidFile) {
  $oldPid = [int](Get-Content $backendPidFile -Raw).Trim()
  $oldProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$oldPid"
  if ($null -eq $oldProcess -or $oldProcess.CommandLine -notmatch 'dockflow.*\.jar') {
    throw "PID anterior não corresponde ao backend DocFlow: $oldPid"
  }
  Stop-Process -Id $oldPid -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 5
}
~~~

Inicie o JAR novo com as variáveis já carregadas:

~~~powershell
$backendStart = @{
  FilePath = 'java'
  ArgumentList = @('-jar', 'E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar')
  WorkingDirectory = 'E:\Projects\docflow\backend'
  RedirectStandardOutput = $backendOut
  RedirectStandardError = $backendErr
  PassThru = $true
}
$backendProcess = Start-Process @backendStart
$backendProcess.Id | Set-Content -LiteralPath $backendPidFile
~~~

Espere até dois minutos pelo health check:

~~~powershell
$backendHealthy = $false
1..60 | ForEach-Object {
  try {
    $health = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 2
    if ($health.status -eq 'UP') { $backendHealthy = $true; return }
  } catch { }
  Start-Sleep -Seconds 2
}
if (-not $backendHealthy) {
  Get-Content $backendErr -Tail 100
  throw 'Backend não ficou saudável em até 120 segundos'
}
~~~

### 5. Testar e gerar o frontend

~~~powershell
Set-Location E:\Projects\docflow\frontend
node --version
npm --version
npm ci
npm test -- --watch=false
npm run build
~~~

O bundle deve ser gerado em:

E:\Projects\docflow\frontend\dist\docflow-frontend\browser

Confirme que não há segredo ou endpoint de infraestrutura no bundle:

~~~powershell
$bundleMatches = rg -n -i '(minio|rabbitmq|postgresql|localhost:5432|localhost:9000|localhost:5672|password|secret|objectKey)' .\dist\docflow-frontend
if ($LASTEXITCODE -eq 0) {
  $bundleMatches
  throw 'Referência de infraestrutura ou segredo encontrada no bundle'
}
~~~

Para o smoke local, inicie o Angular com o proxy versionado:

~~~powershell
$frontendOut = Join-Path $runtimeDir 'frontend.out.log'
$frontendErr = Join-Path $runtimeDir 'frontend.err.log'
$frontendStart = @{
  FilePath = 'npm.cmd'
  ArgumentList = @('start', '--', '--host', '127.0.0.1', '--port', '4200')
  WorkingDirectory = 'E:\Projects\docflow\frontend'
  RedirectStandardOutput = $frontendOut
  RedirectStandardError = $frontendErr
  PassThru = $true
}
$frontendProcess = Start-Process @frontendStart
$frontendProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'frontend.pid')
~~~

O build de produção usa /documents e não deve ser servido em localhost:4200 sem
um reverse proxy da mesma origem. O smoke local usa npm start e /api/documents.

### 6. Smoke pós-deploy

1. Abra http://localhost:4200/documents.
2. Confirme que a listagem mostra os sete campos e todos os status persistidos.
3. Crie um documento por HTTP para obter um caso controlado:

   ~~~powershell
   $headersFile = Join-Path $runtimeDir 'create.headers'
   $bodyFile = Join-Path $runtimeDir 'create.body'
   curl.exe -sS -D $headersFile -o $bodyFile -F 'file=@E:\Projects\docflow\backend\pom.xml;type=application/xml' http://localhost:8080/documents
   Get-Content $headersFile
   Get-Content $bodyFile
   ~~~

4. Confirme 201 Created, Location, status PENDING e sizeBytes maior que zero.
5. Guarde o identificador retornado e aguarde COMPLETED:

   ~~~powershell
   $documentId = '<DOCUMENT_ID>'
   $deadline = (Get-Date).AddSeconds(60)
   do {
     $document = Invoke-RestMethod "http://localhost:8080/documents/$documentId"
     if ($document.status -eq 'COMPLETED') { break }
     if ($document.status -eq 'FAILED') {
       throw "Documento terminou em FAILED: $documentId"
     }
     Start-Sleep -Seconds 2
   } while ((Get-Date) -lt $deadline)
   if ($document.status -ne 'COMPLETED') {
     throw "Documento não terminou em COMPLETED: $documentId"
   }
   ~~~

6. Valide listagem e download:

   ~~~powershell
   Invoke-RestMethod http://localhost:8080/documents
   $downloadHeaders = Join-Path $runtimeDir 'download.headers'
   $downloadFile = Join-Path $runtimeDir 'download.bin'
   curl.exe -sS -D $downloadHeaders -o $downloadFile "http://localhost:8080/documents/$documentId/content"
   Get-Content $downloadHeaders
   Get-Item $downloadFile
   ~~~

   Confirme 200, bytes não vazios, Content-Type, Content-Length,
   Content-Disposition: attachment e Cache-Control: no-store.

7. Tente o download de um documento PENDING, PROCESSING ou FAILED, quando houver
   um disponível, e confirme 409/DOCUMENT_CONTENT_NOT_AVAILABLE.
8. Na interface, cancele uma confirmação de exclusão e confirme que nenhum DELETE
   foi enviado.
9. Para um documento de teste confirmado, clique em Excluir, confirme a ação e
   valide que o item desaparece da listagem e que o DELETE responde 204.
10. Reabra /documents/new e /documents/{documentId} para confirmar que upload e
    consulta existentes continuam funcionando.
11. Registre data/hora, deployRef, hashes, documentId do smoke e resultado dos checks
    fora do repositório. Não registre valores de segredo.

Se qualquer check falhar, interrompa a publicação e preserve logs, containers,
mensagens, staging e dados para investigação.

## Publicação do estado aprovado

Só execute esta seção depois da aprovação explícita e de todos os checks anteriores.
O push registra a entrega no GitHub; não publica automaticamente um servidor:

~~~powershell
Set-Location E:\Projects\docflow
$currentBranch = git branch --show-current
git diff --check
git status --short
git add -A
git restore --staged -- infra/.env backend/dockflow.pid backend/rollback 2>$null
git status --short
git diff --cached --check

Revise a lista staged. Se houver qualquer caminho fora do diff aprovado da feature,
execute git restore --staged -- <caminho> e pare para revisão; não faça commit de
arquivos de runtime, .env, dumps ou alterações de outra feature.

git commit -m "feat: listar baixar e excluir documentos"
git push origin $currentBranch
git log -1 --oneline
~~~

Não faça force push. Não inclua infra/.env, logs, PIDs, dumps ou credenciais.

## Rollback executável

Siga esta sequência em caso de falha. Ela reverte a aplicação local sem apagar
PostgreSQL, RabbitMQ, MinIO, mensagens ou objetos.

1. Pare novas requisições pelo frontend e anote o horário. Não execute DELETE amplo,
   purge de filas, remoção de buckets, DROP DATABASE ou docker compose down -v.
2. Abra um novo PowerShell e defina os caminhos:

   ~~~powershell
   Set-Location E:\Projects\docflow
   $runtimeDir = Join-Path $env:TEMP 'docflow-listagem-deploy'
   $backendPidFile = Join-Path $runtimeDir 'backend.pid'
   $frontendPidFile = Join-Path $runtimeDir 'frontend.pid'
   $rollbackJar = 'E:\Projects\docflow\backend\rollback\dockflow-previous.jar'
   if (-not (Test-Path -LiteralPath $rollbackJar)) {
     throw "JAR anterior ausente: $rollbackJar"
   }
   ~~~

3. Pare o frontend somente se o PID corresponder a npm ou ng:

   ~~~powershell
   if (Test-Path $frontendPidFile) {
     $frontendPid = [int](Get-Content $frontendPidFile -Raw).Trim()
     $frontendInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$frontendPid"
     if ($null -eq $frontendInfo -or $frontendInfo.CommandLine -notmatch 'npm|ng serve') {
       throw "PID não corresponde ao frontend DocFlow: $frontendPid"
     }
     Stop-Process -Id $frontendPid -ErrorAction SilentlyContinue
   }
   ~~~

4. Pare o backend somente se o PID corresponder a um JAR do DocFlow:

   ~~~powershell
   if (-not (Test-Path $backendPidFile)) {
     throw "PID do backend não encontrado: $backendPidFile"
   }
   $backendPid = [int](Get-Content $backendPidFile -Raw).Trim()
   $backendInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$backendPid"
   if ($null -eq $backendInfo -or $backendInfo.CommandLine -notmatch 'dockflow.*\.jar') {
     throw "PID não corresponde ao backend DocFlow: $backendPid"
   }
   Stop-Process -Id $backendPid -ErrorAction SilentlyContinue
   Start-Sleep -Seconds 5
   if (Get-Process -Id $backendPid -ErrorAction SilentlyContinue) {
     Stop-Process -Id $backendPid -Force
   }
   ~~~

5. Preserve os logs e o estado das dependências:

   ~~~powershell
   Copy-Item (Join-Path $runtimeDir 'backend.out.log') (Join-Path $runtimeDir 'rollback-backend.out.log') -ErrorAction SilentlyContinue
   Copy-Item (Join-Path $runtimeDir 'backend.err.log') (Join-Path $runtimeDir 'rollback-backend.err.log') -ErrorAction SilentlyContinue
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres rabbitmq minio
   docker exec docflow-rabbitmq rabbitmqctl list_queues name messages consumers
   ~~~

   Preserve documentos PENDING/PROCESSING, mensagens e staging. Não tente
   “corrigir” a falha removendo dados.

6. Registre o hash do JAR anterior e inicie-o com a mesma infra/.env:

   ~~~powershell
   Get-FileHash -LiteralPath $rollbackJar -Algorithm SHA256
   $rollbackOut = Join-Path $runtimeDir 'rollback-backend.out.log'
   $rollbackErr = Join-Path $runtimeDir 'rollback-backend.err.log'
   $rollbackStart = @{
     FilePath = 'java'
     ArgumentList = @('-jar', $rollbackJar)
     WorkingDirectory = 'E:\Projects\docflow\backend'
     RedirectStandardOutput = $rollbackOut
     RedirectStandardError = $rollbackErr
     PassThru = $true
   }
   $rollbackProcess = Start-Process @rollbackStart
   $rollbackProcess.Id | Set-Content -LiteralPath $backendPidFile
   ~~~

7. Aguarde o backend anterior ficar saudável:

   ~~~powershell
   $rollbackHealthy = $false
   1..60 | ForEach-Object {
     try {
       $rollbackHealth = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 2
       if ($rollbackHealth.status -eq 'UP') { $rollbackHealthy = $true; return }
     } catch { }
     Start-Sleep -Seconds 2
   }
   if (-not $rollbackHealthy) {
     Get-Content $rollbackErr -Tail 100
     throw 'Rollback interrompido: JAR anterior não ficou saudável'
   }
   ~~~

8. Não republique mensagens nem remova staging automaticamente. Para cada
   documentId afetado, registre estado, presença do objeto final, presença do staging
   e existência da mensagem antes de qualquer replay seletivo.
9. Se a publicação no GitHub já tiver ocorrido, reverta o commit publicado com um
   novo commit, sem force push:

   ~~~powershell
   git fetch origin
   $failedCommit = '<SHA-do-commit-publicado-com-falha>'
   git revert --no-edit $failedCommit
   git push origin (git branch --show-current)
   ~~~

   Se houver conflito, execute git revert --abort e escale a revisão; não resolva
   conflito em pânico.
10. Registre horário, commit que falhou, ref/JAR restaurado, hashes, documentIds
    afetados e resultado do health check. O rollback termina somente com o artefato
    anterior saudável ou com o bloqueio explicitamente escalado.

## Gate de aprovação

Este documento não executa deploy e não aprova a promoção. A publicação só começa
depois da revisão dos checks, dos valores de ambiente e do ref/artefato.

**Aprovado pra deploy?**
