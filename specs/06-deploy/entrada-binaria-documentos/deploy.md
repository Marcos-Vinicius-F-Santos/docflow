# Procedimento de Deploy — Recebimento de conteúdo binário no registro de documentos

> Documento histórico do estado anterior à consolidação do backend operacional. Para o
> procedimento vigente, use `specs/06-deploy/registro-binario-e-armazenamento-assincrono-documentos/deploy.md`.

Ambiente: execução local/homologação do DocFlow com backend Spring Boot, PostgreSQL e
MinIO via Docker Compose

Data: 2026-09-16

Observação: `specs/06-deploy/deploy-template.md` não existe literalmente neste
repositório. Este procedimento segue o formato do modelo disponível em
`specs/06-deploy/politica-reconciliacao-result-unknown/deploy.md`.

O repositório não contém Dockerfile, imagem publicada, pipeline CI/CD ou alvo de deploy
externo. Os passos abaixo são o procedimento real para promover o JAR localmente.

Bloqueio atual: o `HEAD` versionado ainda expõe somente o `POST /documents` JSON e o
storage existente com `store`, `exists` e `delete`. A entrada multipart e a leitura do
staging aparecem apenas no worktree local e não devem ser promovidas até serem commitadas,
revisadas e testadas.

## Pré-condições

- [x] Checklist de convergência da feature foi preenchido e a revisão foi considerada
  aprovada pelo solicitante.
- [x] Marcos aprovou explicitamente este procedimento em 2026-09-16.
- [ ] O diff aprovado foi separado do restante do worktree. `git status --short` não
  pode apresentar alterações não aprovadas.
- [ ] Existe uma revisão Git anterior conhecida e compatível com o schema atualmente
  aplicado, registrada antes do `pull`, para rollback.
- [ ] A revisão Git selecionada contém a entrada multipart da API e a leitura necessária
  do staging. No `HEAD` atual, essa condição não é atendida.
- [ ] Docker Desktop está iniciado e Java 21 está disponível.
- [x] A ADR-004 sobre staging assíncrono está aceita.
- [ ] Existe configuração executável para `MinioClient`, `DocumentStaging` e
  `DocumentStorageRequestPublisher`. Esta configuração foi deliberadamente adiada
  para a entrega conjunta com RabbitMQ; sem este item o POST multipart não completa
  staging/publicação e o endpoint não deve ser habilitado.
- [ ] O publisher RabbitMQ, exchange, fila, publisher confirm e a configuração do broker
  foram entregues pela feature posterior de mensageria. O `infra/docker-compose.yml`
  atual não possui serviço RabbitMQ.
- [x] O limite HTTP está configurado no artefato que será promovido:
  `spring.servlet.multipart.max-file-size=52428800B` e
  `spring.servlet.multipart.max-request-size=52428800B`.
- [ ] O arquivo `E:\Projects\docflow\infra\.env` existe localmente e não está
  versionado.

## Variáveis de ambiente e segredos

O arquivo `infra/.env` real deve conter os valores abaixo. Nunca usar o valor de exemplo
da senha do MinIO em homologação/produção e nunca imprimir o conteúdo do arquivo.

| Variável | Ação | Uso |
|---|---|---|
| `POSTGRES_DB` | manter/preencher | Banco usado pelo Compose e pela aplicação |
| `POSTGRES_USER` | manter/preencher | Usuário do PostgreSQL |
| `POSTGRES_PASSWORD` | manter/preencher com segredo forte | Senha do PostgreSQL |
| `MINIO_ROOT_USER` | manter/preencher | Usuário do MinIO local |
| `MINIO_ROOT_PASSWORD` | manter/preencher com segredo forte | Senha do MinIO local |
| `MINIO_STAGING_BUCKET` | adicionar, se ausente | Deve ser `docflow-staging` |

Não existe atualmente uma variável de endpoint/credencial do MinIO consumida pelo
backend, nem variáveis RabbitMQ suportadas pelo código. Não inventar nomes para essas
variáveis: o deploy da feature fica bloqueado até o wiring dos adaptadores definir e
consumir essa configuração.

## Passos do deploy

1. A partir de `E:\Projects\docflow`, confirmar o commit/diff exato que foi aprovado:

   ```powershell
   Set-Location E:\Projects\docflow
   git status --short
   git diff --check
   git rev-parse HEAD
   ```

   Se houver qualquer alteração não aprovada, parar. Não executar o deploy a partir de
   um worktree misturado.

2. Criar ou atualizar `E:\Projects\docflow\infra\.env` manualmente pelo mecanismo
   seguro do ambiente, com estas chaves:

   ```text
   POSTGRES_DB=<nome do banco>
   POSTGRES_USER=<usuario do banco>
   POSTGRES_PASSWORD=<segredo do banco>
   MINIO_ROOT_USER=<usuario do MinIO>
   MINIO_ROOT_PASSWORD=<segredo do MinIO>
   MINIO_STAGING_BUCKET=docflow-staging
   ```

   Não colocar placeholders no ambiente promovido, não versionar o arquivo e não
   executar comandos que exibam as senhas.

3. Validar o Compose antes de criar ou alterar containers:

   ```powershell
   docker compose --env-file E:\Projects\docflow\infra\.env `
     -f E:\Projects\docflow\infra\docker-compose.yml config
   ```

   O resultado deve conter somente os serviços atualmente definidos (`postgres` e
   `minio`) e os volumes `postgres-data` e `minio_data`. Este Compose não sobe RabbitMQ;
   portanto, não prosseguir para um fluxo assíncrono real sem um manifesto aprovado que
   inclua o broker e seus nomes operacionais.

4. Subir a infraestrutura local:

   ```powershell
   docker compose --env-file E:\Projects\docflow\infra\.env `
     -f E:\Projects\docflow\infra\docker-compose.yml up -d postgres minio
   docker compose --env-file E:\Projects\docflow\infra\.env `
     -f E:\Projects\docflow\infra\docker-compose.yml ps
   ```

5. Carregar as variáveis sem imprimir seus valores e aguardar os serviços:

   ```powershell
   Get-Content E:\Projects\docflow\infra\.env |
     Where-Object { $_ -match '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD|MINIO_ROOT_USER|MINIO_ROOT_PASSWORD|MINIO_STAGING_BUCKET)=' } |
     ForEach-Object {
       $name, $value = $_ -split '=', 2
       Set-Item -Path "Env:$name" -Value $value
     }

   docker compose --env-file E:\Projects\docflow\infra\.env `
     -f E:\Projects\docflow\infra\docker-compose.yml exec -T postgres `
     sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
   Invoke-WebRequest -UseBasicParsing http://localhost:9000/minio/health/live
   ```

   Só continuar quando o PostgreSQL responder `accepting connections` e o health check
   do MinIO retornar HTTP 200.

6. Criar e validar o bucket separado de staging usando as credenciais já carregadas:

   ```powershell
   mc alias set docflow http://localhost:9000 $env:MINIO_ROOT_USER $env:MINIO_ROOT_PASSWORD
   mc mb --ignore-existing docflow/docflow-staging
   mc ls docflow/docflow-staging
   ```

   Não tornar o bucket público. Não configurar lifecycle/retention automaticamente:
   essa política não foi aprovada nesta feature.

7. Antes de empacotar, confirmar o limite HTTP multipart:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   $properties = Get-Content .\src\main\resources\application.properties -Raw
   if ($properties -notmatch 'spring\.servlet\.multipart\.max-file-size=52428800B') {
     throw 'Limite HTTP max-file-size de 52428800B não está configurado.'
   }
   if ($properties -notmatch 'spring\.servlet\.multipart\.max-request-size=52428800B') {
     throw 'Limite HTTP max-request-size de 52428800B não está configurado.'
   }
   ```

   Se o comando falhar, parar. A validação funcional no staging não substitui o limite
   HTTP preventivo. No estado atual, as duas propriedades já foram adicionadas.

8. Executar compilação, suíte completa e empacotamento do backend:

   ```powershell
   .\mvnw.cmd -q clean package
   ```

   O Docker precisa estar disponível porque os testes de integração usam Testcontainers.
   Se qualquer teste falhar, parar. O artefato esperado é
   `E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar`.

9. Registrar a revisão atualmente em execução antes de atualizar o código. O arquivo
   de registro deve ser preservado fora de `target` e não deve ser sobrescrito até o
   deploy seguinte:

   ```powershell
   Set-Location E:\Projects\docflow
   $deployBranch = git branch --show-current
   $previousRef = git rev-parse HEAD
   $previousRef | Set-Content E:\Projects\docflow\backend\deploy-previous-ref.txt
   if (-not (Test-Path -LiteralPath E:\Projects\docflow\backend\deploy-previous-ref.txt)) {
     throw 'Não foi possível registrar a revisão de rollback.'
   }
   ```

   O `pull` deve ser feito somente depois dessa captura e com o worktree limpo:

   ```powershell
   git pull --ff-only origin $deployBranch
   ```

10. Parar uma instância anterior do backend, se houver, usando somente o PID registrado:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   if (Test-Path -LiteralPath .\dockflow.pid) {
     $oldPid = [int](Get-Content .\dockflow.pid)
     Get-Process -Id $oldPid -ErrorAction SilentlyContinue
     Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
   }
   ```

11. Iniciar o JAR aprovado e registrar o PID:

   ```powershell
   $stdout = Join-Path $env:TEMP 'dockflow-stdout.log'
   $stderr = Join-Path $env:TEMP 'dockflow-stderr.log'
   $jar = 'E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar'
   $process = Start-Process -FilePath java `
     -ArgumentList '-jar', $jar `
     -WorkingDirectory 'E:\Projects\docflow\backend' `
     -RedirectStandardOutput $stdout `
     -RedirectStandardError $stderr `
     -PassThru
   $process.Id | Set-Content E:\Projects\docflow\backend\dockflow.pid
   ```

   O Flyway será executado automaticamente. Não executar migrations manualmente nem
   usar `docker compose down -v`.

12. Confirmar inicialização:

   ```powershell
   Invoke-RestMethod -Uri http://localhost:8080/actuator/health
   Get-Content $stderr
   ```

   O health deve indicar `UP` e o log não pode conter falha de datasource, Hibernate,
   Flyway, MinIO ou publisher.

13. Executar smoke test do contrato antigo e do novo somente depois de confirmar que a
    revisão selecionada contém a entrada multipart e a leitura necessária do staging. No
    `HEAD` atual, parar neste ponto: o JSON antigo ainda é o contrato existente e não há
    endpoint binário versionado.

    O JSON antigo deve retornar `400` somente quando a mudança de contrato estiver na
    revisão promovida.
   Para o multipart, usar um arquivo aprovado pelo ambiente; `docs/api.md` é apenas
   documentação e não deve ser usado como documento de negócio sem validação de tipo.

   ```powershell
   $documentPath = 'E:\caminho\para\documento-aprovado.pdf'
   if (-not (Test-Path -LiteralPath $documentPath)) {
     throw "Arquivo de smoke test não encontrado: $documentPath"
   }

   $jsonStatus = curl.exe -sS -o $null -w '%{http_code}' `
     -X POST http://localhost:8080/documents `
     -H 'Content-Type: application/json' `
     --data '{"originalFilename":"old.pdf","contentType":"application/pdf","sizeBytes":1}'
   if ($jsonStatus -ne '400') { throw "Contrato JSON retornou HTTP $jsonStatus em vez de 400." }

   $multipartResponse = Join-Path $env:TEMP 'dockflow-document-response.json'
   $multipartStatus = curl.exe -sS -o $multipartResponse -w '%{http_code}' `
     -X POST http://localhost:8080/documents `
     -F "file=@$documentPath;type=application/pdf"
   if ($multipartStatus -ne '201') { throw "Multipart retornou HTTP $multipartStatus em vez de 201." }
   $document = Get-Content $multipartResponse -Raw | ConvertFrom-Json
   if (-not $document.id) { throw 'Resposta multipart não contém document id.' }
   mc stat "docflow/docflow-staging/staging/$($document.id)"
   ```

   O smoke test só é válido quando o publisher confirma a publicação. Sem RabbitMQ e
   sem os beans de staging/publisher, não considerar um health `UP` como sucesso da
   feature.

## Ordem de migration de banco

Esta feature não cria migration nem altera o schema. O Flyway deve validar e aplicar
somente as migrations já existentes, sem editar V1–V4. O tamanho do documento é um
metadado já existente; não executar SQL manual para preparar o recebimento binário.

Se o Flyway falhar, parar o backend, preservar os volumes `postgres-data` e `minio_data`,
coletar logs e seguir o rollback abaixo. Nunca apagar volumes para tentar corrigir uma
falha de migration.

## Validação depois do deploy

- `GET http://localhost:8080/actuator/health` retorna `UP`.
- `POST /documents` JSON retorna `400`.
- Multipart válido retorna `201` somente depois do staging e da publicação confirmada.
- O objeto `docflow-staging/staging/{documentId}` existe e tem o tamanho enviado.
- A resposta não contém aceitação antes da gravação/publicação.
- O log contém o `documentId` para correlação e não contém falhas não tratadas.
- PostgreSQL continua acessível e os endpoints de consulta existentes continuam
  respondendo.
- Não há mensagem com binário ou `InputStream`; o payload contém somente a referência e
  os metadados do contrato `DocumentStorageRequested`.

## Procedimento de rollback

### Se o smoke test falhar ou a aplicação não iniciar

1. Não apagar PostgreSQL, MinIO, bucket de staging ou mensagens.
2. Parar exatamente o processo registrado:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   if (Test-Path -LiteralPath .\dockflow.pid) {
     $appPid = [int](Get-Content .\dockflow.pid)
     Get-Process -Id $appPid -ErrorAction SilentlyContinue
     Stop-Process -Id $appPid -Force -ErrorAction SilentlyContinue
   }
   ```

3. Guardar os logs antes de qualquer nova tentativa:

   ```powershell
   Copy-Item $env:TEMP\dockflow-stdout.log E:\Projects\docflow\backend\dockflow-rollback-failure-stdout.log -ErrorAction SilentlyContinue
   Copy-Item $env:TEMP\dockflow-stderr.log E:\Projects\docflow\backend\dockflow-rollback-failure-stderr.log -ErrorAction SilentlyContinue
   ```

4. Recuperar a revisão estável registrada antes do `pull`:

   ```powershell
   Set-Location E:\Projects\docflow
   $rollbackRef = (Get-Content E:\Projects\docflow\backend\deploy-previous-ref.txt -Raw).Trim()
   if ($rollbackRef -notmatch '^[0-9a-f]{7,40}$') {
     throw "Rollback interrompido: revisão inválida: $rollbackRef"
   }
   git status --short
   if (git status --porcelain) {
     throw 'Rollback interrompido: worktree não está limpo.'
   }
   git fetch origin
   git switch --detach $rollbackRef
   if ((git rev-parse HEAD) -ne $rollbackRef) {
     throw 'Rollback interrompido: revisão restaurada não corresponde à registrada.'
   }
   ```

   Este é o rollback pelo código obtido/atualizado via Git. Um `git pull` sem registrar
   a revisão anterior não é suficiente para desfazer uma atualização.

5. Recompilar a revisão estável usando o mesmo ambiente PostgreSQL, sem apagar volumes:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   .\mvnw.cmd -q clean package
   $rollbackJar = 'E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar'
   if (-not (Test-Path -LiteralPath $rollbackJar)) {
     throw "Rollback interrompido: JAR recompilado não encontrado: $rollbackJar"
   }
   $rollbackStdout = Join-Path $env:TEMP 'dockflow-rollback-stdout.log'
   $rollbackStderr = Join-Path $env:TEMP 'dockflow-rollback-stderr.log'
   $rollbackProcess = Start-Process -FilePath java `
     -ArgumentList '-jar', $rollbackJar `
     -WorkingDirectory 'E:\Projects\docflow\backend' `
     -RedirectStandardOutput $rollbackStdout `
     -RedirectStandardError $rollbackStderr `
     -PassThru
   $rollbackProcess.Id | Set-Content E:\Projects\docflow\backend\dockflow.pid
   ```

6. Confirmar saúde da versão anterior:

   ```powershell
   Invoke-RestMethod -Uri http://localhost:8080/actuator/health
   ```

   Se não retornar `UP`, parar o processo, manter os containers ativos e escalar o
   incidente com os logs. Não tentar uma versão ainda mais antiga sem confirmar que ela
   entende o schema Flyway já aplicado.

### Se o staging tiver sido gravado ou a publicação tiver resultado inconclusivo

1. Manter o objeto `staging/{documentId}` no bucket `docflow-staging`.
2. Não publicar uma cópia manual nem purgar a fila; correlacionar pelo `documentId`.
3. Preservar a mensagem e seguir a idempotência/retry da feature de mensageria quando o
   broker estiver operacional.
4. Se a persistência PostgreSQL tiver falhado depois do staging, registrar o
   `documentId`, os horários e a chave do objeto para investigação.
5. Não executar `mc rm --recursive`, `docker compose down -v`, `DROP`, `DELETE` manual
   ou edição de `flyway_schema_history`.
6. Fazer limpeza seletiva somente após decisão operacional aprovada e com backup/registro
   da correlação.

### Se for necessário interromper também a infraestrutura local

1. Primeiro parar o backend e preservar logs.
2. Executar somente se a interrupção de PostgreSQL/MinIO estiver autorizada:

   ```powershell
   docker compose --env-file E:\Projects\docflow\infra\.env `
     -f E:\Projects\docflow\infra\docker-compose.yml stop postgres minio
   ```

3. Não usar `down -v`; os volumes contêm dados e objetos que precisam sobreviver ao
   rollback.

## Bloqueios conhecidos antes da aprovação

- Não há publisher/topologia RabbitMQ no código ou no Compose atual; isso fica para a
  feature posterior de mensageria.
- O `HEAD` atual não possui entrada multipart na API nem leitura do objeto de staging.
  As implementações correspondentes encontradas no worktree ainda não estão commitadas.
- Não há beans de `MinioClient`, staging e publisher registrados no contexto da aplicação;
  isso fica para a configuração posterior junto ao RabbitMQ e bloqueia a ativação do
  POST multipart completo.
- O rollback depende de registrar uma revisão Git estável antes do `pull` e de manter o
  worktree limpo.
- O template de deploy solicitado não existe no caminho literal informado.

Este procedimento não aprova o deploy. A aprovação deve ser dada explicitamente por
Marcos após resolver ou aceitar conscientemente os bloqueios documentados.
