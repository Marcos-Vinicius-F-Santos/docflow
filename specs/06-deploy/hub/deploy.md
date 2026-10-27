# Deploy e rollback — Hub de documentos

Status: procedimento operacional; não constitui aprovação de deploy.

Feature: `hub`  
Referências: [Spec da feature](../../03-features/hub/spec.md), [ADR-007](../../02-arquitetura/DECISAO/ADR-007-retry-e-exclusao-atrasada-documentos.md), [contrato HTTP](../../../docs/api.md) e [operação](../../../docs/document-registration-operations.md).

## Pré-condições

- Marcos revisou o diff, a migration V6, o ADR-007 e este procedimento.
- Docker Desktop, Java 21 e Node/npm estão disponíveis.
- `infra/.env` existe localmente e não será commitado.
- PostgreSQL, RabbitMQ e MinIO serão preservados; não usar `docker compose down -v`.
- O artefato da versão anterior está disponível pelo mecanismo oficial de rollback.

Validar a árvore e a configuração sem imprimir segredos:

```powershell
Set-Location E:\Projects\docflow
git status --short
git diff --check
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
```

## Migrations e contratos

O backend deve aplicar V6 depois de V1–V5:

- `failure_count` começa em `0` e é exposto como `falhas`;
- `is_excluded` começa em `false`;
- `exclusion_scheduled_at` começa nulo;
- o índice parcial cobre documentos excluídos com prazo preenchido.

O retry só pode publicar para `FAILED`, `falhas < 3`, `isExcluded = false` e staging
existente. A terceira falha agenda exclusão para três horas, sem quarta tentativa. O
retry responde `202`; documento inexistente responde `404`, documento inelegível `409`,
staging indisponível `503` e publicação não confirmada `503`.

## Preparação e validação automatizada

```powershell
Set-Location E:\Projects\docflow\backend
.\mvnw.cmd test

Set-Location E:\Projects\docflow\frontend
npm test -- --watch=false
```

Os testes de integração do backend usam Testcontainers e precisam do Docker Desktop.
O resultado deve ser registrado com data, commit/revisão, quantidade de testes e
falhas. Warnings de testes que exercitam falhas esperadas não substituem um resultado
de teste bem-sucedido.

## Execução local do Hub

```powershell
Set-Location E:\Projects\docflow
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres rabbitmq minio
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
```

Iniciar o backend com as variáveis de `infra/.env` carregadas no processo e iniciar o
frontend pelo procedimento de `docs/frontend.md`. Confirmar:

1. `GET /actuator/health` retorna `UP`.
2. `/` abre o Hub; `/documents/new`, `/documents` e `/documents/{id}` continuam acessíveis.
3. Upload válido retorna `201`, `Location` e `PENDING`, e o usuário permanece no Hub.
4. O documento chega a `COMPLETED`; o objeto final existe e o staging é removido.
5. Documentos `FAILED` exibem `falhas`, `isExcluded` e `exclusionScheduledAt`.
6. `POST /documents/{id}/retry` retorna `202` e reutiliza o staging, sem criar documento.
7. A terceira falha publica mensagem persistente em
   `docflow.document-deletion.delay.3h` e remove a ação de retry.
8. Após o TTL, o consumidor remove objeto final, staging e metadados, confirmando a
   mensagem somente depois da remoção.
9. Exclusão manual antes do TTL é imediata; mensagem tardia é idempotente.

Inspecionar as filas sem apagar mensagens:

```powershell
docker exec docflow-rabbitmq rabbitmqctl list_queues name messages consumers
```

As filas da exclusão são `docflow.document-deletion.delay.3h`,
`docflow.document-deletion.requested` e `docflow.document-deletion.dlq`. A DLQ indica
falha que requer investigação; não é confirmação de exclusão.

## Rollback

1. Interromper novas requisições e registrar horário, versão e `documentId`s afetados.
2. Preservar filas, mensagens, volumes, objetos finais e `staging/{documentId}`.
3. Parar somente o processo da versão nova pelo mecanismo operacional aprovado.
4. Reverter para o artefato anterior. Não remover V6 nem editar dados diretamente.
5. Se houver publicação inconclusiva, tratar a mensagem como potencialmente existente e
   não publicar cópias manuais.
6. Validar health check, conectividade e integridade de PostgreSQL, RabbitMQ e MinIO.
7. Reconciliar seletivamente documentos `PENDING`/`PROCESSING` e mensagens de exclusão
   somente com correlação por `documentId` e aprovação operacional.

O rollback de código não desfaz exclusões já executadas. Qualquer limpeza de staging,
objetos, filas ou registros exige decisão operacional específica; nunca usar purge,
remoção em massa ou exclusão de volumes como atalho.

## Registro da execução

Registrar fora do repositório:

- data/hora e revisão executada;
- resultado de `git diff --check`, build e testes;
- status do Compose e das filas;
- `documentId` usado no smoke test;
- estados observados (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`);
- resultado de retry, terceira falha, exclusão atrasada e exclusão manual;
- aprovação de Marcos antes de qualquer deploy.
