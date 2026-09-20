# Procedimento de Deploy — Política de reconciliação para resultado desconhecido do storage (`RESULT_UNKNOWN`)

Este procedimento é histórico. O procedimento vigente está em
`specs/06-deploy/registro-binario-e-armazenamento-assincrono-documentos/deploy.md` e
inclui o scheduler interno, o bootstrap local e as migrations V4/V5.

Ambiente: execução local/homologação do DocFlow com PostgreSQL e MinIO via Docker Compose

Data: 2026-09-14

Observação de escopo: o Produto e a Arquitetura não definem deploy ou hospedagem externa
nesta fase. Este é, portanto, o procedimento real para promover o backend localmente. O
repositório não contém Dockerfile, imagem publicada, pipeline CI/CD ou artefato de release.

## Pré-condições

- [x] Checklist de convergência da feature revisado e aprovado para seguir ao portão de deploy.
- [ ] Roteiro de teste manual da feature executado. A reconciliação é disparada pelo
  scheduler interno; não há endpoint público para esse mecanismo.
- [ ] Variáveis de ambiente/segredos não estão hardcoded nem commitados. Criar `infra/.env` local com valores reais; não usar o valor de exemplo de `MINIO_ROOT_PASSWORD` e não versionar esse arquivo.
- [ ] O diff final foi revisado por Marcos e este procedimento foi aprovado.
- [x] O `infra/docker-compose.yml` foi corrigido e validado. A configuração agora reconhece `postgres` e `minio` como serviços e `postgres-data` e `minio_data` como volumes de nível superior.
- [ ] Docker Desktop está iniciado e Java 21 está disponível.
- [ ] Um artefato de rollback compatível com o schema V4 foi separado antes da promoção. O repositório não fornece hoje um diretório de releases; sem esse artefato, o rollback de aplicação não é executável.

## Passos do deploy

1. A partir de `E:\Projects\docflow`, confirmar que o deploy será feito a partir do commit revisado, e não de mudanças locais não aprovadas:

   ```powershell
   git status --short
   git diff --check
   ```

   Se aparecer qualquer alteração além do diff aprovado, parar e revisar antes de continuar.

2. Criar `E:\Projects\docflow\infra\.env` manualmente, sem imprimir os valores no terminal, contendo exatamente estas chaves:

   ```text
   POSTGRES_DB=<nome do banco>
   POSTGRES_USER=<usuario do banco>
   POSTGRES_PASSWORD=<senha forte do banco>
   MINIO_ROOT_USER=<usuario do MinIO>
   MINIO_ROOT_PASSWORD=<senha forte do MinIO>
   ```

   O arquivo é ignorado pelo Git. A feature não adiciona variável de ambiente ao backend:
   `application.properties` usa apenas `POSTGRES_DB`, `POSTGRES_USER` e
   `POSTGRES_PASSWORD`; as variáveis `MINIO_*` são necessárias somente para iniciar o
   serviço MinIO do Compose. Não há configuração de endpoint/bucket MinIO ligada ao
   contexto Spring nesta versão.

3. Validar o manifesto antes de criar ou alterar containers:

   ```powershell
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config
   ```

   O comando deve terminar sem erro e mostrar `postgres` e `minio` como serviços, com
   `postgres-data` e `minio_data` como volumes de nível superior. Se aparecer
   `services.volumes additional properties 'minio', 'postgres-data' not allowed`, voltar
   ao passo 1: o manifesto ainda está inválido.

4. Subir somente a infraestrutura local usada pelo backend:

   ```powershell
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres minio
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
   ```

   Só continuar quando o PostgreSQL responder `accepting connections` e os dois serviços
   aparecerem em execução.

5. Executar compilação, testes e empacotamento do backend. O Docker precisa estar
   disponível porque os testes de integração usam Testcontainers:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   .\mvnw.cmd -q clean package
   ```

   Se qualquer teste falhar ou o Maven não conseguir iniciar o Testcontainers, parar o
   deploy. O JAR esperado é `backend\target\dockflow-0.0.1-SNAPSHOT.jar`.

6. No mesmo terminal em que o backend será iniciado, carregar somente as três variáveis
   do PostgreSQL a partir do `.env`, sem exibir a senha:

   ```powershell
   Get-Content E:\Projects\docflow\infra\.env |
     Where-Object { $_ -match '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD)=' } |
     ForEach-Object {
       $name, $value = $_ -split '=', 2
       Set-Item -Path "Env:$name" -Value $value
     }
   ```

7. Iniciar o JAR e guardar o PID para permitir parada rápida:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   $stdout = Join-Path $env:TEMP 'dockflow-stdout.log'
   $stderr = Join-Path $env:TEMP 'dockflow-stderr.log'
   $process = Start-Process -FilePath java -ArgumentList '-jar', '.\target\dockflow-0.0.1-SNAPSHOT.jar' -WorkingDirectory (Get-Location) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
   $process.Id | Set-Content .\dockflow.pid
   ```

   O Flyway será executado automaticamente na inicialização da aplicação. Não executar
   `V4__add_reconciliation_attempts.sql` manualmente.

8. Confirmar que a aplicação iniciou sem erro de datasource, Hibernate ou Flyway:

   ```powershell
   Get-Content $stdout -Wait
   Invoke-RestMethod -Uri http://localhost:8080/actuator/health
   ```

   Esperar o log de aplicação iniciada e uma resposta de saúde com `status` igual a
   `UP`. Se o processo encerrar, consultar `$stderr` e parar o procedimento.

## Ordem de migration de banco (se houver)

O Flyway deve aplicar `V1`, `V2`, `V3` e depois `V4__add_reconciliation_attempts.sql`.
As migrations V1 a V3 não devem ser editadas. A V4 é expand-only e adiciona
`documents.reconciliation_attempts BIGINT NOT NULL DEFAULT 0`.

Depois que o backend iniciar, validar a ordem e o resultado no PostgreSQL:

```powershell
docker compose --env-file E:\Projects\docflow\infra\.env -f E:\Projects\docflow\infra\docker-compose.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
docker compose --env-file E:\Projects\docflow\infra\.env -f E:\Projects\docflow\infra\docker-compose.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = ''documents'' AND column_name = ''reconciliation_attempts'';"'
```

A validação só passa se houver uma linha V4 com `success = true` e a coluna estiver
presente, não nula e com default zero.

## Validação depois do deploy

- `GET http://localhost:8080/actuator/health` responde `UP`.
- O log não contém falha de conexão com PostgreSQL, falha de validação do Hibernate ou falha do Flyway.
- A tabela `flyway_schema_history` mostra V4 aplicada depois de V3.
- Registros existentes continuam legíveis e novos registros iniciam com `reconciliation_attempts = 0`.
- A suíte `mvnw.cmd -q clean package` terminou com sucesso antes da inicialização.
- Não há smoke test HTTP para a reconciliação porque o scheduler é interno e não há
  endpoint público. A validação deve observar a agenda persistida, o claim atômico e os
  estados `PROCESSING`, `COMPLETED` e `FAILED` por testes e consultas operacionais.

## Procedimento de rollback

### Se o Flyway falhar antes de V4 ser registrada como aplicada

1. Parar o backend:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   $appPid = [int](Get-Content .\dockflow.pid)
   Stop-Process -Id $appPid -Force -ErrorAction SilentlyContinue
   ```

2. Verificar se V4 não foi aplicada:

   ```powershell
   docker compose --env-file E:\Projects\docflow\infra\.env -f E:\Projects\docflow\infra\docker-compose.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history WHERE version = ''4'';"'
   ```

3. Não remover coluna, não editar `flyway_schema_history` e não executar `DROP TABLE`.
   Corrigir a causa indicada no log e iniciar novamente o mesmo artefato. A V4 contém
   uma alteração única de schema e o PostgreSQL deve mantê-la atômica.

### Se V4 já foi aplicada com sucesso e for necessário voltar a aplicação

1. Parar exatamente o processo cujo PID está em `E:\Projects\docflow\backend\dockflow.pid`:

   ```powershell
   Set-Location E:\Projects\docflow\backend
   $appPid = [int](Get-Content .\dockflow.pid)
   Get-Process -Id $appPid
   Stop-Process -Id $appPid -Force
   ```

2. Manter PostgreSQL e a coluna `documents.reconciliation_attempts`. Não executar
   rollback destrutivo da migration e não apagar objetos do MinIO.

3. Iniciar o JAR de rollback previamente separado, que deve ser compatível com um banco
   contendo V4. O caminho do artefato precisa ser preenchido antes da aprovação; o
   repositório atual não contém esse artefato:

   ```powershell
   $rollbackJar = 'E:\caminho\preparado\dockflow-rollback-compativel-com-V4.jar'
   if (-not (Test-Path $rollbackJar)) { throw "Artefato de rollback não encontrado: $rollbackJar" }
   $stdout = Join-Path $env:TEMP 'dockflow-rollback-stdout.log'
   $stderr = Join-Path $env:TEMP 'dockflow-rollback-stderr.log'
   $process = Start-Process -FilePath java -ArgumentList '-jar', $rollbackJar -WorkingDirectory (Split-Path $rollbackJar) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
   $process.Id | Set-Content E:\Projects\docflow\backend\dockflow.pid
   ```

4. Confirmar `http://localhost:8080/actuator/health` com `UP` e repetir a consulta de
   `flyway_schema_history`. Se o artefato disponível não for compatível com V4, não
   iniciar uma versão anterior às cegas: manter o serviço parado e preparar uma versão
   de rollback que carregue a migration V4 ou outra estratégia aprovada.

5. Manter os containers PostgreSQL e MinIO ativos. Só executar
   `docker compose ... down` se a intenção for também interromper a infraestrutura local;
   não usar `down -v`, pois isso apagaria os volumes locais e os dados.

## Notas de recuperação de dados (se aplicável)

- A V4 não altera o conteúdo dos documentos: registros existentes recebem contador zero.
- A recuperação normal é reverter a aplicação mantendo o schema expandido.
- Não há backup, restauração automática ou remoção de objetos definida por esta feature.
- Se a migration ou a aplicação falhar, preservar os volumes `postgres-data` e
  `minio_data` e coletar os logs antes de qualquer ação destrutiva.
- A definição do disparador da reconciliação e o tratamento operacional de objetos
  eventualmente órfãos pertencem a Specs futuras.

## Bloqueios conhecidos antes da aprovação

- O MinIO não foi iniciado nesta execução: o `infra/.env` não possui as chaves `MINIO_*`
  e o daemon não conseguiu obter `minio/minio:latest` do registry. O backend atual não
  depende desse serviço para iniciar porque ainda não registra a configuração do adaptador.
- Não existe alvo de deploy externo, imagem Docker do backend ou artefato de rollback
  versionado/configurado.
- A aplicação atual não registra um bean/configuração de MinIO nem um disparador da
  reconciliação; portanto, este deploy valida o backend e a migration, não um fluxo
  end-to-end de `RESULT_UNKNOWN`.
