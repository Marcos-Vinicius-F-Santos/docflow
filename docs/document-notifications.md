# Notificacoes de documentos

Este documento descreve o contrato e a operacao da feature
`notificacoes-documentos`. O backend publica eventos de ciclo de vida no RabbitMQ,
entrega o contrato publico pelo WebSocket e, quando configurado, envia uma copia por
e-mail via Web3Forms. O frontend nunca acessa RabbitMQ, PostgreSQL, MinIO ou a API da
Web3Forms diretamente.

## Contrato publico

O JSON e versionado com `schemaVersion: 1`. Todos os eventos possuem
`eventId`, `schemaVersion`, `type`, `documentId` e `occurredAt`.

Os tipos validos sao:

- `DOCUMENT_PROCESSING_STARTED`, com `status: PROCESSING`;
- `DOCUMENT_COMPLETED`, com `status: COMPLETED`;
- `DOCUMENT_FAILED`, com `status: FAILED` e `failureCount`;
- `DOCUMENT_DELETION_WARNING`, com `status: FAILED`, `failureCount` e
  `exclusionScheduledAt`;
- `DOCUMENT_DELETED`, sem estado do documento.

Campos opcionais sem significado para o evento sao omitidos. O contrato nao expoe
`objectKey`, bucket, staging, exchange, fila, routing key, API key ou credenciais.

## WebSocket

O endpoint e `GET /ws/notifications` por upgrade para WebSocket. O Hub conecta ao
abrir, reconecta com atraso progressivo ate 30 segundos e consulta `GET /documents`
ao recuperar a conexao. A consulta REST e a fonte de verdade; a primeira versao nao
possui replay nem historico persistido. Um evento perdido enquanto nao havia conexao
nao e reproduzido.

Em ambiente local, o backend permite a origem `http://localhost:4200` por padrao. Para
outro host ou porta, configure `DOCFLOW_NOTIFICATION_WEBSOCKET_ALLOWED_ORIGINS` com uma
lista separada por virgulas no ambiente do backend.

Todas as abas ativas da instalacao recebem o broadcast. O frontend deduplica por
`eventId` somente durante a sessao da aba e descarta tipos ou payloads invalidos sem
interromper o Hub. A falha de uma sessao nao impede a entrega para as demais.

## Topologia RabbitMQ

Todos os elementos abaixo sao duraveis e as mensagens sao persistentes. Publishers
aguardam publisher confirm.

| Elemento | Nome |
|---|---|
| Exchange de eventos | `docflow.document-events` |
| Fila de notificacoes | `docflow.document-notifications.requested` |
| Fila de aviso | `docflow.document-notifications.delay.2h30` |
| DLX/DLQ de notificacoes | `docflow.document-notifications.dlx` / `docflow.document-notifications.dlq` |
| Fila de e-mail | `docflow.document-email-notifications.requested` |
| Routing key de e-mail | `notification.email.requested` |
| Retries de e-mail | `.retry.30s`, `.retry.5m`, `.retry.30m` |
| DLX/DLQ de e-mail | `docflow.document-email-notifications.dlx` / `docflow.document-email-notifications.dlq` |

As routing keys dos eventos sao `document.processing.started`,
`document.completed`, `document.failed`, `document.deletion.warning.scheduled`,
`document.deletion.warning` e `document.deleted`. A fila de aviso usa TTL de 2h30 e
redireciona para `document.deletion.warning`; o prazo total de exclusao do documento
continua sendo de tres horas.

O consumidor de notificacoes confirma a mensagem depois de encaminhar o evento aos
canais. Mensagem invalida, dispatcher indisponivel ou falha de entrega vai para a
DLQ sem alterar o documento. A DLQ exige investigacao por `eventId` e `documentId`;
ela nao significa que o documento foi excluido.

## E-mail e Web3Forms

O e-mail e um canal independente. Timeout, indisponibilidade, HTTP 429 e HTTP 5xx
usam retries de 30 segundos, 5 minutos e 30 minutos; depois da tentativa inicial e
dos tres retries a mensagem vai para a DLQ. Credencial ausente/invalida, destinatario
ausente e erros 4xx que nao sejam 429 sao permanentes e vao diretamente para a DLQ.
Nenhuma falha de e-mail altera o status, contador ou exclusao do documento.

Configure somente no backend:

| Variavel | Uso |
|---|---|
| `WEB3FORMS_ACCESS_KEY` | Access key da Web3Forms; determina a caixa de destino |
| `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` | Destinatario temporario da instalacao |
| `WEB3FORMS_API_URL` | Endpoint da API; padrao `https://api.web3forms.com/submit` |

Sem configuracao explicita, os testes locais nao enviam e-mail real. O adaptador e
testado com HTTP local, sem rede externa. A Web3Forms pode exigir plano pago e
liberacao do IP para chamadas server-side; a configuracao do access key deve ser
validada antes do primeiro envio real.

## Verificacao local

Com Docker Desktop ativo, a evidencia automatizada pode ser executada sem provedor
externo:

```powershell
Set-Location E:\Projects\docflow\backend
.\mvnw.cmd -Dtest=DocumentNotificationRabbitRealIntegrationTest test
.\mvnw.cmd -Dtest=DocumentRegistrationEndToEndTest test
.\mvnw.cmd test

Set-Location E:\Projects\docflow\frontend
npm.cmd exec ng test -- docflow-frontend --watch=false --isolate=false
```

Os testes de integracao iniciam PostgreSQL, RabbitMQ e MinIO via Testcontainers. Para
inspecionar filas em uma instalacao Compose sem consumir mensagens:

```powershell
docker exec docflow-rabbitmq rabbitmqctl list_queues name messages consumers
```

Nao purgue filas nem remova volumes como correcao. Preserve mensagens, objetos e
registros para investigacao correlacionada pelo `documentId`.

## Referencias

- [Contrato HTTP](api.md)
- [Operacao do registro e smoke test](document-registration-operations.md)
- [ADR-008 — RabbitMQ, WebSocket e e-mail](../specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md)
- [Deploy e rollback do Hub](../specs/06-deploy/hub/deploy.md)
