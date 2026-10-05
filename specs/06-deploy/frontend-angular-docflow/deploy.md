# Procedimento de Deploy — Frontend Angular do DocFlow

Ambiente: checkout Windows em `E:\Projects\docflow`, publicado no repositório GitHub
`origin` na branch `feature/DOC-006-document-storage`. A validação local usa backend
Spring Boot em `localhost:8080` e PostgreSQL, RabbitMQ e MinIO em Docker Compose.
Data: 2026-09-20
Status: Aprovado por Marcos em 2026-09-20; publicação no GitHub autorizada.

Neste projeto, deploy significa consolidar o estado aprovado em um commit e fazer
push para o repositório GitHub remoto. O repositório não possui hosting externo,
Dockerfile do frontend, reverse proxy de produção ou pipeline CI/CD; o push não
publica automaticamente uma aplicação executável.

## Pré-condições

- [x] Testes manuais aprovados em `specs/05-verificacao/frontend-angular-docflow/checklist.md`.
- [x] Checklist de convergência da feature concluído.
- [x] Tarefas T-022 a T-025 aprovadas.
- [ ] O diff final e o commit/ref exato foram revisados no checkout que será promovido.
- [ ] O acesso de push ao remoto GitHub `origin` está disponível.
- [ ] Docker Desktop está ativo, Java 21 está disponível e Node.js `v24.19.0`/npm `12.0.2` estão disponíveis.
- [ ] `E:\Projects\docflow\infra\.env` existe, não está versionado e contém as credenciais locais necessárias.
- [ ] O artefato anterior de frontend foi preservado antes de substituir `frontend\dist`.
- [x] Eu, Marcos, revisei este procedimento e o diff final.

Se algum item obrigatório permanecer desmarcado, pare. Não publique o frontend nem
altere volumes, banco, filas ou buckets para contornar uma pré-condição.

## Variáveis de ambiente e segredos

O frontend não recebe segredos. Os arquivos Angular usam somente URLs relativas:

- desenvolvimento: `environment.development.ts` usa `/api/documents`, encaminhado pelo
  proxy para `http://localhost:8080/documents`;
- produção: `environment.ts` usa `/documents`, exigindo que o servidor estático esteja
  sob a mesma origem da API ou atrás de um reverse proxy aprovado.

Não coloque credenciais, tokens, URLs de MinIO, RabbitMQ ou PostgreSQL nos arquivos
`frontend/src/environments/`, no bundle ou em qualquer arquivo versionado.

Para executar o backend local, `infra/.env` deve conter, sem imprimir os valores no
terminal:

- PostgreSQL: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`;
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` e
  `RABBITMQ_MANAGEMENT_PORT`;
- MinIO administrativo: `MINIO_ROOT_USER` e `MINIO_ROOT_PASSWORD`;
- MinIO da aplicação: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`,
  `MINIO_STAGING_BUCKET` e `MINIO_BUCKET`;
- política operacional: `DOCFLOW_MESSAGING_ENABLED=true`,
  `DOCFLOW_RECONCILIATION_ENABLED=true`, `DOCFLOW_RECONCILIATION_MAX_ATTEMPTS=5`,
  `DOCFLOW_RECONCILIATION_BACKOFF=PT1M,PT5M,PT15M,PT30M,PT60M`,
  `DOCFLOW_RECONCILIATION_SCHEDULE_INTERVAL=PT1M` e
  `DOCFLOW_PROCESSING_TIMEOUT=PT3S`.

Carregue essas variáveis apenas no processo atual do PowerShell:

```powershell
Set-Location E:\Projects\docflow
$envLines = Get-Content .\infra\.env |
  Where-Object { $_ -and -not $_.Trim().StartsWith('#') }
foreach ($envLine in $envLines) {
  $name, $value = $envLine -split '=', 2
  Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
```

Nunca use `${POSTGRES_USER}` para ler uma variável no PowerShell. Use
`$env:POSTGRES_USER`; `${POSTGRES_USER}` só é interpolação válida no arquivo do
Compose, não no comando PowerShell.

## Preparação e validação local antes da publicação

1. Abra um PowerShell e confirme o checkout:

   ```powershell
   Set-Location E:\Projects\docflow
   git status --short
   git diff --check
   git rev-parse HEAD
   ```

   Pare se o `git diff --check` falhar. As alterações ainda não precisam estar
   commitadas nesta etapa; elas serão revisadas e consolidadas na seção de publicação.

2. Confirme a configuração e suba a infraestrutura sem remover volumes:

   ```powershell
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres rabbitmq minio
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
   ```

   Não execute `docker compose down -v`. Se o bootstrap falhar, consulte os logs e
   corrija a variável ou credencial indicada antes de continuar:

   ```powershell
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres rabbitmq minio bootstrap
   ```

3. Carregue as variáveis do backend no mesmo PowerShell, usando o bloco da seção
   “Variáveis de ambiente e segredos”. Em seguida, compile e teste o backend:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   .\mvnw.cmd -q clean verify
   ```

   O resultado esperado é 92 testes, 0 falhas, 0 erros e 0 ignorados. Pare se houver
   qualquer divergência.

4. Inicie o backend local e guarde o PID e os logs fora do repositório:

   ```powershell
   $runtimeDir = Join-Path $env:TEMP 'docflow-frontend-deploy'
   New-Item -ItemType Directory -Force $runtimeDir | Out-Null
   $backendOut = Join-Path $runtimeDir 'backend.out.log'
   $backendErr = Join-Path $runtimeDir 'backend.err.log'
   $backendProcess = Start-Process -FilePath java `
     -ArgumentList '-jar', 'E:\Projects\docflow\backend\target\dockflow-0.0.1-SNAPSHOT.jar' `
     -WorkingDirectory 'E:\Projects\docflow\backend' `
     -RedirectStandardOutput $backendOut `
     -RedirectStandardError $backendErr `
     -PassThru
   $backendProcess.Id | Set-Content (Join-Path $runtimeDir 'backend.pid')
   ```

5. Aguarde a aplicação ficar saudável:

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
     Get-Content $backendErr -Tail 100
     throw 'Backend não ficou saudável em até 120 segundos.'
   }
   ```

6. Preserve o artefato anterior antes de gerar o novo build. O backup precisa ficar
   dentro do workspace, mas fora do artefato atual:

   ```powershell
   Set-Location E:\Projects\docflow\frontend
   $currentArtifact = Join-Path (Get-Location) 'dist\docflow-frontend\browser'
   $rollbackArtifact = Join-Path (Get-Location) 'rollback\previous-browser'
   if (Test-Path -LiteralPath $rollbackArtifact) {
     throw "Backup anterior já existe; preserve-o ou revise-o antes de continuar: $rollbackArtifact"
   }
   if (Test-Path -LiteralPath $currentArtifact) {
     New-Item -ItemType Directory -Force (Split-Path $rollbackArtifact) | Out-Null
     Copy-Item -LiteralPath $currentArtifact -Destination $rollbackArtifact -Recurse
   }
   ```

   Se não houver artefato anterior, registre explicitamente que este é o primeiro
   build local; nesse caso o rollback só poderá interromper o servidor frontend, não
   restaurar uma versão anterior.

7. Instale exatamente as dependências travadas, execute os testes e gere o build:

   ```powershell
   node --version
   npm --version
   npm ci
   npm test -- --watch=false
   npm run build
   ```

   O build esperado fica em:

   ```text
   E:\Projects\docflow\frontend\dist\docflow-frontend\browser
   ```

8. Verifique que o artefato não contém segredos nem endpoints de infraestrutura:

   ```powershell
   $matches = rg -n -i '(minio|rabbitmq|postgresql|localhost:5432|localhost:9000|localhost:5672|password|secret)' .\dist\docflow-frontend
   if ($LASTEXITCODE -eq 0) { $matches; throw 'Marcador de segredo ou infraestrutura encontrado no bundle.' }
   ```

9. Para a validação local da interface, use o servidor de desenvolvimento com proxy:

   ```powershell
   npm start -- --host 127.0.0.1 --port 4200
   ```

   Abra `http://localhost:4200/documents/new` e confirme: upload de PDF, progresso,
   navegação para o identificador, estados `PENDING`/`PROCESSING`/`COMPLETED` ou
   `FAILED`, documento inexistente e erro operacional.

   Esse comando é validação local, não publicação de produção. O build de produção
   usa `/documents` e só pode ser servido em um host com encaminhamento da mesma
   origem para o backend. Como este repositório não define esse host ou reverse proxy,
   pare aqui em vez de inventar CORS, BFF ou hospedagem externa.

## Publicação no GitHub — deploy efetivo

Execute esta seção somente depois de concluir a validação local e revisar o conteúdo
que será publicado. Ela publica a branch atual, não faz merge automático em `main`.

1. Confirme a branch e o remoto, sem incluir artefatos locais de execução:

   ```powershell
   Set-Location E:\Projects\docflow
   $branch = git branch --show-current
   if ($branch -ne 'feature/DOC-006-document-storage') {
     throw "Branch inesperada: $branch"
   }
   git remote get-url origin
   git diff --check
   git add -A -- . ':(exclude)backend/dockflow.pid' ':(exclude)backend/rollback/**'
   git status --short
   ```

   Não continue se aparecer `infra/.env`, senha, token, PID, JAR de rollback ou outro
   artefato gerado. Os artefatos locais excluídos permanecem no checkout e não são
   publicados.

2. Revise o conteúdo staged e confirme que o diff corresponde à feature aprovada:

   ```powershell
   git diff --cached --check
   git diff --cached --stat
   git diff --cached --name-only
   ```

3. Crie o commit de publicação:

   ```powershell
   git commit -m 'feat: publish DocFlow frontend and document storage'
   ```

   Se não houver alterações para commit, não crie um commit vazio; publique o commit
   aprovado já existente somente depois de confirmar o SHA com `git log -1 --oneline`.

4. Publique a branch no GitHub:

   ```powershell
   git push -u origin $branch
   ```

5. Confirme a publicação:

   ```powershell
   git status --short --branch
   git log -1 --oneline
   git ls-remote --heads origin $branch
   ```

   O SHA retornado para `origin/$branch` deve ser o mesmo SHA exibido localmente. O
   deploy termina neste ponto; não há etapa de publicação automática em hosting.

## Ordem de migration de banco (se houver)

Não há migration do frontend. O deploy do frontend não altera PostgreSQL, Flyway,
RabbitMQ ou MinIO.

Se o backend for promovido junto no ambiente local, o Flyway aplica automaticamente as
migrations existentes na ordem V1, V2, V3, V4 e V5. Não execute SQL manual, não altere
`flyway_schema_history` e não use `docker compose down -v`.

## Validação depois do deploy

No ambiente local, confirme:

1. `GET http://localhost:8080/actuator/health` responde `UP`.
2. `http://localhost:4200/documents/new` exibe a tela de upload sem erro no console.
3. Um PDF válido de até 50 MiB recebe `201` e navega para o detalhe.
4. O detalhe exibe nome, tipo, tamanho, timestamps e o estado retornado pelo backend.
5. O polling para em `COMPLETED`, `FAILED` ou `404`.
6. Arquivo incompatível, documento inexistente e falha operacional exibem mensagens
   compreensíveis.
7. O bundle não contém credenciais nem endpoints de infraestrutura.
8. Nenhum schema, volume, fila ou bucket foi alterado pelo deploy do frontend.

Não considere o health check isolado suficiente: o fluxo upload → consulta → estado
terminal também precisa passar.

## Procedimento de rollback no GitHub

Use este rollback quando o último commit publicado precisar ser desfeito. Ele cria um
novo commit de reversão e preserva o histórico; não use `reset --hard` nem force push.

1. Identifique a branch e o commit publicado que precisa ser revertido:

   ```powershell
   Set-Location E:\Projects\docflow
   $branch = git branch --show-current
   git fetch origin
   git log --oneline -n 5 "origin/$branch"
   ```

   Registre o SHA do commit publicado com falha e confirme que ele é o topo de
   `origin/$branch`. Se houver commits posteriores, pare e faça a revisão manual da
   ordem de reversão antes de continuar.

2. Reverta o commit publicado:

   ```powershell
   $failedCommit = '<SHA-do-commit-publicado-com-falha>'
   if ((git rev-parse "origin/$branch") -ne $failedCommit) {
     throw 'Rollback interrompido: o commit informado não é o topo da branch remota.'
   }
   git checkout $branch
   git pull --ff-only origin $branch
   git revert --no-edit $failedCommit
   ```

   Se houver conflito, execute `git revert --abort`, preserve o estado atual e escale
   a revisão; não resolva o conflito no escuro durante o incidente.

3. Publique o commit de reversão:

   ```powershell
   git push origin $branch
   git log -1 --oneline
   git ls-remote --heads origin $branch
   ```

4. Depois de confirmar o SHA de reversão no GitHub, pare novas submissões e pressione
   `Ctrl+C` no terminal que executa `npm start`.
   Se o terminal não estiver disponível, localize somente um processo Angular na porta
   4200 e valide a linha de comando antes de encerrá-lo:

   ```powershell
   $listener = Get-NetTCPConnection -LocalPort 4200 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
   if ($listener) {
     $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
     if ($processInfo.CommandLine -notmatch 'ng serve|npm.*start') {
       throw 'Rollback interrompido: a porta 4200 não pertence claramente ao frontend DocFlow.'
     }
     Stop-Process -Id $listener.OwningProcess
   }
   ```

5. Se o artefato atual foi copiado para um servidor estático externo, não tente
   adivinhar o destino. Restaure somente o diretório de backup registrado pelo
   operador. Para o artefato local deste projeto:

   ```powershell
   Set-Location E:\Projects\docflow\frontend
   $currentArtifact = Join-Path (Get-Location) 'dist\docflow-frontend\browser'
   $rollbackArtifact = Join-Path (Get-Location) 'rollback\previous-browser'
   if (-not (Test-Path -LiteralPath $rollbackArtifact)) {
     throw "Rollback interrompido: backup anterior ausente em $rollbackArtifact"
   }
   if (Test-Path -LiteralPath $currentArtifact) {
     Remove-Item -LiteralPath $currentArtifact -Recurse -Force
   }
   Copy-Item -LiteralPath $rollbackArtifact -Destination $currentArtifact -Recurse
   ```

6. Se a versão anterior precisar ser servida localmente, inicie novamente o servidor
   apontando para o checkout/artefato anterior aprovado. Não rode `npm ci`, `npm run
build` ou comandos de banco até confirmar qual ref deve ser restaurada.

7. Mantenha o backend e os containers ativos. O frontend não possui estado persistido
   próprio; não execute `docker compose down -v`, purge de RabbitMQ, `DROP DATABASE`
   ou remoção de objetos MinIO como parte do rollback.

8. Confirme novamente o health check do backend e execute uma consulta `GET` de um
   documento conhecido. Se o backend também tiver sido alterado, use o procedimento de
   rollback do backend em seu arquivo de deploy separado; não misture os dois rollbacks.

9. Registre horário, versão que falhou, versão restaurada, caminho do backup e o
   resultado da validação. O rollback termina somente depois que a versão anterior
   estiver acessível ou o bloqueio estiver explicitamente escalado.

## Notas de recuperação de dados

- O frontend não persiste dados próprios e não exige migration.
- Um upload já aceito pelo backend não deve ser reenviado automaticamente durante o
  rollback; preserve o `documentId` e consulte o backend quando ele estiver saudável.
- Não remova documentos `PENDING`/`PROCESSING`, mensagens RabbitMQ ou objetos de
  staging para corrigir uma falha visual do frontend.
- PostgreSQL, RabbitMQ e MinIO permanecem fora do rollback do frontend.

Este procedimento foi aprovado por Marcos. O deploy significa o push da branch
aprovada para o GitHub; ele não publica automaticamente um servidor de produção.
