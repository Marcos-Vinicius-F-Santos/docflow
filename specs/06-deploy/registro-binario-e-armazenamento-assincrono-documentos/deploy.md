# Procedimento de Deploy — Backend operacional do DocFlow

Ambiente: local, `E:\Projects\docflow`, Docker Compose + JAR Spring Boot no Windows
Alvo: processo Java local; PostgreSQL, RabbitMQ e MinIO permanecem em Docker Compose
Data da revisão: 2026-09-19

Este procedimento cobre somente o ambiente local. O projeto não possui imagem Docker do
backend, pipeline CI/CD ou ambiente externo de homologação/produção.

## Pré-condições

- [x] O roteiro de teste manual foi executado em specs/05-verificacao.
- [x] O checklist de convergência foi preenchido.
- [ ] O diff final da feature está separado e o commit ou ref exato foi identificado.
- [ ] O arquivo E:\Projects\docflow\infra\.env existe, não está versionado e contém os valores locais necessários.
- [ ] O checkout usado para a entrega está limpo ou todas as alterações da feature estão explicitamente revisadas.
- [ ] O artefato anterior aprovado existe em E:\Projects\docflow\backend\rollback\dockflow-previous.jar.
- [ ] O utilitário `mc` do MinIO está instalado no host para validar objetos no smoke test.
- [ ] Docker Desktop está ativo e Java 21 está disponível no PATH.
- [ ] O aprovador revisou o diff final, este procedimento e o checklist de convergência.

Se qualquer item obrigatório permanecer desmarcado, interrompa o procedimento e não inicie o backend.

## Variáveis de ambiente e segredos

A entrega não deve adicionar segredos ao repositório. O arquivo infra/.env é local e deve permanecer fora do commit.

Preencha ou confirme estas variáveis em infra/.env:

- POSTGRES_DB=docflow
- POSTGRES_USER=docflow
- POSTGRES_PASSWORD=<senha local do PostgreSQL>
- MINIO_ROOT_USER=docflow
- MINIO_ROOT_PASSWORD=<senha local do MinIO>
- MINIO_ENDPOINT=http://localhost:9000
- MINIO_ACCESS_KEY=<usuário da aplicação no MinIO>
- MINIO_SECRET_KEY=<senha do usuário da aplicação no MinIO>
- MINIO_STAGING_BUCKET=docflow-staging
- MINIO_BUCKET=docflow-documents
- RABBITMQ_HOST=localhost
- RABBITMQ_PORT=5672
- RABBITMQ_USER=docflow
- RABBITMQ_PASSWORD=<senha local do RabbitMQ>
- DOCFLOW_RECONCILIATION_ENABLED=true
- DOCFLOW_RECONCILIATION_MAX_ATTEMPTS=5
- DOCFLOW_RECONCILIATION_BACKOFF=PT1M,PT5M,PT15M,PT30M,PT60M

As mudanças de configuração relevantes para esta feature são:

1. As credenciais do MinIO e do RabbitMQ precisam estar definidas para o publisher confirmado e o armazenamento funcionarem.
2. O bucket de staging precisa ser diferente do bucket final.
3. Os limites de upload de 50 MiB e os parâmetros de confirmação do RabbitMQ já estão versionados na configuração da aplicação; não devem ser substituídos por segredo.
4. Não copie valores reais para infra/.env.example, para o código ou para qualquer arquivo versionado.

Depois de preencher o arquivo, carregue as variáveis somente no processo atual do PowerShell:

    Set-Location E:\Projects\docflow
    Get-Content .\infra\.env | Where-Object { $_ -match '^[^#].+=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        Set-Item -Path "Env:$name" -Value $value
    }

Valide que POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD, MINIO_ACCESS_KEY, MINIO_SECRET_KEY e RABBITMQ_PASSWORD não estão vazias. Para o uso local simples, as credenciais da aplicação podem ser configuradas para um usuário local do MinIO, desde que continuem apenas no arquivo infra/.env. Se forem criados usuários separados no MinIO, conceda somente a política necessária aos buckets da aplicação.

### Mudanças de configuração e segredos

Em um checkout novo, copie `infra/.env.example` para `infra/.env` e substitua os
valores de exemplo pelos valores locais. As variáveis obrigatórias para esta feature são:

- PostgreSQL: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` e
  `RABBITMQ_MANAGEMENT_PORT`.
- MinIO administrativo, usado somente pelo bootstrap:
  `MINIO_ROOT_USER` e `MINIO_ROOT_PASSWORD`.
- MinIO da aplicação: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`,
  `MINIO_STAGING_BUCKET` e `MINIO_BUCKET`.
- Política operacional: `DOCFLOW_MESSAGING_ENABLED=true`,
  `DOCFLOW_RECONCILIATION_ENABLED=true`, `DOCFLOW_RECONCILIATION_MAX_ATTEMPTS=5`,
  `DOCFLOW_RECONCILIATION_BACKOFF=PT1M,PT5M,PT15M,PT30M,PT60M`,
  `DOCFLOW_RECONCILIATION_SCHEDULE_INTERVAL=PT1M` e
  `DOCFLOW_PROCESSING_TIMEOUT=PT3S`.

Não há alteração de segredo no código, no JAR ou no Compose versionado. Não reutilize
credenciais root do MinIO como credenciais da aplicação por conveniência; o bootstrap
cria/valida o usuário da aplicação e a política limitada aos dois buckets. Se os volumes
já existirem com credenciais antigas, pare e diagnostique antes de trocar valores; não
use remoção de volumes como correção.

## Passos do deploy

1. Abra o PowerShell e entre no repositório:

    Set-Location E:\Projects\docflow

2. Faça a checagem de segurança do diretório e do rollback:

    git status --short
    git diff --check
    Test-Path .\backend\rollback\dockflow-previous.jar
    Get-ChildItem .\backend\src\main\resources\db\migration
    git rev-parse HEAD

    Pare se houver alterações não revisadas, erro no diff, ou se o artefato anterior não existir.
    Registre também os hashes dos dois JARs antes de iniciar:

    Get-FileHash .\backend\rollback\dockflow-previous.jar -Algorithm SHA256
    if (Test-Path .\backend\target\dockflow-0.0.1-SNAPSHOT.jar) {
        Get-FileHash .\backend\target\dockflow-0.0.1-SNAPSHOT.jar -Algorithm SHA256
    }

   Para uma execução de validação a partir de checkout limpo, copie
   `infra/.env.example` para `infra/.env` antes de carregar as variáveis. Em qualquer
   checkout já usado, preserve alterações do operador e diagnostique os volumes antes
   de alterar credenciais:

       docker volume ls --filter name=docflow
       docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
       docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres rabbitmq minio bootstrap

3. Carregue e valide as variáveis de ambiente:

    Get-Content .\infra\.env | Where-Object { $_ -match '^[^#].+=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        Set-Item -Path "Env:$name" -Value $value
    }

    $required = @(
        'POSTGRES_PASSWORD',
        'MINIO_ROOT_PASSWORD',
        'MINIO_ACCESS_KEY',
        'MINIO_SECRET_KEY',
        'RABBITMQ_PASSWORD',
        'MINIO_STAGING_BUCKET',
        'MINIO_BUCKET'
    )
    foreach ($name in $required) {
        if ([string]::IsNullOrWhiteSpace((Get-Item "Env:$name").Value)) {
            throw "Variável obrigatória ausente: $name"
        }
    }
    if ($env:MINIO_STAGING_BUCKET -eq $env:MINIO_BUCKET) {
        throw 'Os buckets de staging e final precisam ser diferentes'
    }

4. Valide a configuração e suba a infraestrutura local:

    docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
    docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres minio rabbitmq
    docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
    docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps

   Não use docker compose down -v. Os volumes postgres-data, minio_data e rabbitmq_data devem ser preservados.

5. Espere os serviços locais responderem:

    Invoke-WebRequest http://localhost:9000/minio/health/live -UseBasicParsing
    Test-NetConnection localhost -Port 5432
    Test-NetConnection localhost -Port 5672

   Se algum serviço falhar, consulte os logs antes de iniciar a aplicação:

    docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres minio rabbitmq

6. O bootstrap já valida as credenciais root e da aplicação MinIO, cria os buckets
   `docflow-staging` e `docflow-documents` de forma idempotente e termina com erro
   explícito quando uma dependência ou credencial está incorreta. Não crie buckets
   manualmente como parte do caminho principal.

7. Compile e execute a suíte automatizada antes de iniciar o JAR:

    Set-Location E:\Projects\docflow\backend
    .\mvnw.cmd -q clean verify

   O resultado esperado da versão revisada é 92 testes, 0 falhas, 0 erros e 0 ignorados. Se o resultado for diferente, interrompa e registre a divergência antes do deploy.

8. Prepare os arquivos temporários de execução fora do repositório:

    $runtimeDir = Join-Path $env:TEMP 'docflow-runtime'
    New-Item -ItemType Directory -Force $runtimeDir | Out-Null
    Remove-Item (Join-Path $runtimeDir 'dockflow.pid') -ErrorAction SilentlyContinue

9. Inicie o artefato gerado. O processo deve usar as variáveis carregadas no passo 3:

    $startArgs = @{
        FilePath = 'java'
        ArgumentList = @('-jar', 'E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar')
        WorkingDirectory = 'E:\Projects\docflow\backend'
        RedirectStandardOutput = (Join-Path $runtimeDir 'dockflow.out.log')
        RedirectStandardError = (Join-Path $runtimeDir 'dockflow.err.log')
        PassThru = $true
    }
    $process = Start-Process @startArgs
    $process.Id | Set-Content (Join-Path $runtimeDir 'dockflow.pid')

10. Aguarde o health check da aplicação:

    $healthy = $false
    1..60 | ForEach-Object {
        try {
            $response = Invoke-WebRequest http://localhost:8080/actuator/health -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200 -and $response.Content -match '"status"\s*:\s*"UP"') {
                $healthy = $true
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) {
        Get-Content (Join-Path $runtimeDir 'dockflow.err.log') -Tail 100
        throw 'A aplicação não ficou saudável em até 120 segundos'
    }

11. Execute o smoke test multipart e registre o documentId:

    $response = curl.exe -sS -i -F 'file=@E:\Projects\docflow\backend\pom.xml;type=application/xml' http://localhost:8080/documents
    $response

   Confirme manualmente que a resposta contém HTTP 201, um header Location, status PENDING e sizeBytes maior que zero. Extraia o ID do Location ou do corpo e guarde-o para os passos seguintes.

12. Extraia o `documentId` do header `Location` e aguarde o processamento, sem considerar
   um único `GET` inicial como confirmação:

    $documentId = '<DOCUMENT_ID>'
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $document = Invoke-RestMethod "http://localhost:8080/documents/$documentId"
        if ($document.status -eq 'COMPLETED') { break }
        if ($document.status -eq 'FAILED') { throw "Documento terminou em FAILED: $documentId" }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    if ($document.status -ne 'COMPLETED') { throw "Documento não terminou em COMPLETED: $documentId" }

   Valide os objetos com o `mc` instalado no host. O alias abaixo usa as credenciais root
   somente para inspeção local; não registre os comandos com os valores expostos:

    mc alias set docflow http://localhost:9000 $env:MINIO_ROOT_USER $env:MINIO_ROOT_PASSWORD
    mc stat "docflow/$env:MINIO_BUCKET/documents/$documentId"
    mc stat "docflow/$env:MINIO_STAGING_BUCKET/staging/$documentId"

   O primeiro comando deve localizar o objeto final. O segundo deve falhar com objeto
   inexistente depois do consumo bem-sucedido. Se o `mc stat` do staging retornar sucesso,
   pare e investigue: o staging não pode ser removido antes da confirmação final.

13. Confirme que o RabbitMQ local está ativo e que as filas da aplicação existem:

    docker exec docflow-rabbitmq rabbitmqctl list_queues name messages consumers
    docker exec docflow-rabbitmq rabbitmqctl list_consumers queue_name consumer_tag ack_required prefetch_count

   Deve haver um consumidor ativo nas filas esperadas, com `prefetch_count` igual a `1`.

14. Registre fora do repositório a data/hora, a ref usada, o documentId do smoke test, o PID do backend e o resultado dos checks. Mantenha os serviços locais ativos somente se isso for desejado para o desenvolvimento; a execução deste procedimento não publica nada fora da máquina local.

   Se o bootstrap ou o smoke test falhar, registre a mensagem e preserve os volumes
   para investigação. O diagnóstico de uma credencial incorreta deve terminar com
   falha explícita do bootstrap; não marque o procedimento como aprovado apenas porque
   os containers iniciaram.

## Ordem de migration de banco

As migrations V4 e V5 fazem parte do estado persistido da reconciliação. Não crie SQL
manual e não edite o histórico do Flyway.

Em um banco vazio, a aplicação deve aplicar as migrations existentes na ordem V1, V2,
V3, V4 e V5. V4 e V5 pertencem à reconciliação persistida; não execute SQL manual nem
edite o histórico do Flyway.

Depois que a aplicação estiver saudável, confirme o histórico aplicado com:

```powershell
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

As linhas de V1 a V5 devem estar presentes e com `success = t`. Pare o procedimento se
houver migration ausente ou com falha.

Em um banco já utilizado localmente, deixe o Flyway aplicar somente migrations pendentes. Não remova a V4, não altere dados diretamente e não use rollback destrutivo do volume.

## Validação depois do deploy

Confirme todos os itens abaixo:

- GET /actuator/health responde status UP.
- POST /documents aceita multipart e responde HTTP 201.
- A resposta inicial contém Location, status PENDING e o tamanho real do arquivo.
- O publisher confirmado entrega a mensagem ao RabbitMQ local.
- O documento passa para COMPLETED após o consumo.
- O objeto existe em docflow-documents/documents/<DOCUMENT_ID>.
- O objeto temporário não permanece em docflow-staging/<DOCUMENT_ID> após sucesso.
- O RabbitMQ lista as filas e não apresenta erro de conexão; o listener usa consumidor
  único, `prefetch=1` e concorrência `1..1`. A confirmação operacional de `prefetch=1`
  vem do `list_consumers`; a concorrência `1..1` também deve permanecer alinhada à
  configuração versionada e aos testes de integração da feature.
- O scheduler está habilitado conforme `DOCFLOW_RECONCILIATION_ENABLED` e consulta
  documentos `PROCESSING` elegíveis sem endpoint público.
- Os logs não mostram erro de startup, falha de conexão, segredo ou stack trace não tratado.
- O smoke test não alterou o comportamento de endpoints existentes cobertos pela suíte.

Health UP isolado não é suficiente: a confirmação precisa incluir o fluxo multipart, RabbitMQ e MinIO.

## Procedimento de rollback

Siga exatamente esta sequência. Não apague volumes, mensagens, registros ou objetos para
tentar “limpar” o estado.

1. Pare de enviar novas requisições para `POST /documents` e anote a hora do rollback.

2. Abra um novo PowerShell e defina os caminhos operacionais:

   ```powershell
   Set-Location E:\Projects\docflow
   $runtimeDir = Join-Path $env:TEMP 'docflow-runtime'
   $pidFile = Join-Path $runtimeDir 'dockflow.pid'
   $rollbackJar = 'E:\Projects\docflow\backend\rollback\dockflow-previous.jar'
   New-Item -ItemType Directory -Force $runtimeDir | Out-Null
   ```

3. Não execute `docker compose down -v`, `DROP DATABASE`, limpeza ampla de buckets,
   purge de filas ou `DELETE` amplo no banco. Mantenha `postgres-data`, `minio_data` e
   `rabbitmq_data` intactos.

4. Pare somente o processo registrado pelo procedimento. Se o PID não existir, pare e
   investigue; não mate processos por tentativa usando a porta 8080:

   ```powershell
   if (-not (Test-Path -LiteralPath $pidFile)) {
       throw "Rollback interrompido: PID do backend não encontrado em $pidFile"
   }
   $appPid = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
   $process = Get-CimInstance Win32_Process -Filter "ProcessId=$appPid"
   if ($null -eq $process -or $process.CommandLine -notmatch 'dockflow.*\.jar') {
       throw "Rollback interrompido: PID $appPid não corresponde a um JAR do DocFlow"
   }
   Stop-Process -Id $appPid -ErrorAction SilentlyContinue
   Start-Sleep -Seconds 5
   if (Get-Process -Id $appPid -ErrorAction SilentlyContinue) {
       Stop-Process -Id $appPid -Force
   }
   ```

5. Preserve os registros `PENDING`/`PROCESSING`, as mensagens RabbitMQ e os objetos de
   staging. Se não for possível confirmar se uma mensagem foi publicada, trate a
   publicação como desconhecida e não apague o staging. Guarde os logs antes de qualquer
   nova tentativa:

   ```powershell
   Copy-Item (Join-Path $runtimeDir 'dockflow.err.log') (Join-Path $runtimeDir 'dockflow-rollback-failure.err.log') -ErrorAction SilentlyContinue
   Copy-Item (Join-Path $runtimeDir 'dockflow.out.log') (Join-Path $runtimeDir 'dockflow-rollback-failure.out.log') -ErrorAction SilentlyContinue
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
   docker exec docflow-rabbitmq rabbitmqctl list_queues name messages consumers
   ```

6. Confirme que o artefato anterior existe e registre seu hash. Não faça `git reset`,
   não descarte alterações locais e não reconstrua o rollback a partir de uma árvore
   não aprovada:

   ```powershell
   if (-not (Test-Path -LiteralPath $rollbackJar)) {
       throw "Rollback interrompido: artefato ausente: $rollbackJar"
   }
   Get-FileHash -LiteralPath $rollbackJar -Algorithm SHA256
   ```

7. Recarregue as variáveis da mesma `infra/.env` usada no deploy, sem imprimir os
   valores, e inicie o JAR anterior:

   ```powershell
   Get-Content .\infra\.env | Where-Object { $_ -match '^[^#].+=' } | ForEach-Object {
       $name, $value = $_ -split '=', 2
       Set-Item -Path "Env:$name" -Value $value
   }
   $rollbackStdout = Join-Path $runtimeDir 'dockflow-rollback.out.log'
   $rollbackStderr = Join-Path $runtimeDir 'dockflow-rollback.err.log'
   $rollbackProcess = Start-Process -FilePath java `
       -ArgumentList '-jar', $rollbackJar `
       -WorkingDirectory 'E:\Projects\docflow\backend' `
       -RedirectStandardOutput $rollbackStdout `
       -RedirectStandardError $rollbackStderr `
       -PassThru
   $rollbackProcess.Id | Set-Content -LiteralPath $pidFile
   ```

8. Aguarde o health check por no máximo dois minutos:

   ```powershell
   $healthy = $false
   1..60 | ForEach-Object {
       try {
           $health = Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 2
           if ($health.status -eq 'UP') { $healthy = $true; return }
       } catch { }
       Start-Sleep -Seconds 2
   }
   if (-not $healthy) {
       Get-Content -LiteralPath $rollbackStderr -Tail 100
       throw 'Rollback interrompido: artefato anterior não ficou saudável'
   }
   ```

9. Não republique automaticamente mensagens novas. A versão anterior pode não conhecer
   o contrato multipart ou a mensagem de processamento. Mantenha mensagens e staging
   para investigação e replay seletivo posterior.

10. Para cada `documentId` afetado, registre fora do repositório o estado no banco, a
    existência do objeto de staging, a existência do objeto final e a presença da
    mensagem. Não faça limpeza automática por prefixo.

11. Registre a causa, o horário, a ref nova, a ref anterior, os hashes, os PIDs e os
    `documentId`s afetados. O rollback termina somente quando o artefato anterior estiver
    saudável ou quando o bloqueio estiver explicitamente escalado.

## Notas de recuperação de dados

PostgreSQL, MinIO e RabbitMQ não participam de uma transação distribuída única. Portanto:

- Se a publicação não puder ser confirmada, preserve o documento como PENDING e preserve o staging.
- Nunca trate uma publicação desconhecida como falha certa.
- Não remova objetos finais durante o rollback.
- Não faça purge de fila para corrigir uma falha da aplicação.
- Qualquer limpeza deve ser seletiva por documentId, após inspeção e aprovação.
- A recuperação de um documento PENDING deve usar o fluxo de reconciliação/reprocessamento previsto no projeto, sem alterar manualmente o histórico do Flyway.

Este procedimento não aprova o deploy. A decisão permanece com o aprovador após revisar o diff, o checklist de convergência, o arquivo infra/.env e a existência do artefato de rollback.

Aprovado pra deploy?
