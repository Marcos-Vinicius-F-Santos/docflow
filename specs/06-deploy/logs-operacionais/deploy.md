# Deploy e operação - logs operacionais

## Escopo

Esta feature adiciona o componente lógico de logs operacionais ao backend existente.
Não há microserviço separado, não há autenticação nesta versão e a consulta é
somente leitura em `GET /logs` e na rota Angular `/logs`.

## Configuração

O ambiente local usa as mesmas credenciais do PostgreSQL e RabbitMQ já documentadas em
[`docs/document-registration-operations.md`](../../../docs/document-registration-operations.md).
Os parâmetros específicos são:

| Variável | Propriedade | Padrão | Uso |
|---|---|---|---|
| `DOCFLOW_LOG_RETENTION` | `docflow.logs.retention` | `PT3H` | Janela de retenção contada de `occurredAt`. |
| `DOCFLOW_LOG_RETENTION_SCHEDULE_INTERVAL` | `docflow.logs.retention-schedule-interval` | `PT1M` | Intervalo do job de limpeza. |
| `DOCFLOW_LOGS_MAX_PAGE_SIZE` | `docflow.logs.max-page-size` | `100` | Limite de `size` em `GET /logs`. |

Não configurar segredos no frontend. O evento de log é sanitizado antes da publicação e
não pode conter senha, token, API key, credencial, binário, bucket ou stack trace não
filtrado.

## Dependências e topologia

Subir PostgreSQL, RabbitMQ e MinIO pelo Compose padrão do projeto e executar o bootstrap
conforme o runbook existente. O backend aplica automaticamente a migration
`V7__create_application_logs.sql`.

O RabbitMQ declara uma topologia própria para logs:

- exchange `docflow.application-logs`;
- fila de persistência `docflow.application-logs.persist`;
- filas de retry `docflow.application-logs.retry.5s`, `.15s` e `.60s`;
- DLX `docflow.application-logs.dlx` e DLQ `docflow.application-logs.dlq`.

Todas as mensagens são persistentes. O publisher usa confirm; o consumidor confirma a
mensagem somente depois da persistência ou da detecção idempotente por `eventId`.

## Smoke test local

1. Valide o Compose sem imprimir valores de segredo:

   ```powershell
   docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
   ```

2. Inicie a infraestrutura e o backend conforme o procedimento do projeto.
3. Gere um evento pelo fluxo normal de documento e consulte:

   ```powershell
   curl.exe "http://localhost:8080/logs?page=0&size=10"
   curl.exe "http://localhost:8080/logs?level=ERROR&documentId=<DOCUMENT_ID>"
   ```

4. Abra `http://localhost:4200/logs` e confirme loading, lista, filtros, paginação,
   estado vazio, erro com retry e vínculo read-only para um `documentId` UUID.
5. Confirme no log local que o evento contém `eventId`, `occurredAt`, nível, serviço,
   componente, operação e mensagem segura. Não procurar nem copiar conteúdo sensível.
6. Para retenção, use o teste de clock controlado; não altere o relógio dos containers
   nem apague volumes para simular expiração.

## Diagnóstico e rollback

Se RabbitMQ estiver indisponível, o fluxo principal deve continuar e o logger local deve
registrar a falha de publicação. Se PostgreSQL estiver indisponível, a API responde
`503` com `LOG_QUERY_UNAVAILABLE`; a resposta não expõe SQL ou stack trace.

Mensagens inválidas ou falhas permanentes devem aparecer na DLQ própria de logs. Preserve
mensagens e volumes antes de qualquer ação corretiva. O rollback é feito implantando uma
versão compatível que ignore a tabela V7; não editar migrations já aplicadas, não executar
`DROP TABLE application_logs` e não usar `docker compose down -v`.

Nenhum deploy externo é autorizado sem revisão e aprovação explícita de Marcos.
