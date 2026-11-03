# Reconciliação de resultado desconhecido do storage

O caso de uso de reconciliação vive na camada de aplicação do domínio `document` e
depende da porta provider-neutral `DocumentStorage`. A verificação usa somente
`exists(objectKey)`; a lógica de aplicação não acessa o SDK do MinIO diretamente.

Quando a existência é confirmada, o domínio move o documento de `PROCESSING` para
`COMPLETED`. Quando não há confirmação e ainda existem tentativas, o documento permanece
em `PROCESSING`. Ao atingir o limite configurado sem confirmação, o domínio move o
documento para `FAILED`.

O contador é persistido em PostgreSQL pela migration `V4`; a próxima elegibilidade é
persistida pela `V5`. O scheduler interno periódico consulta documentos elegíveis,
obtém claim atômico com `FOR UPDATE SKIP LOCKED` e usa os atrasos `1m`, `5m`, `15m`,
`30m` e `60m`. Não há endpoint REST para disparar o caminho principal. RabbitMQ trata
retries e DLQ antes da reconciliação, e a limpeza via `delete(objectKey)` continua fora
do escopo.

Os limites arquiteturais completos estão em `specs/02-arquitetura/ARQUITETURA.md` e no
ADR-005; este arquivo é um resumo operacional.

## Notificações de ciclo de vida

As notificações seguem o ADR-008: o RabbitMQ permanece interno, o backend traduz os
eventos para o contrato JSON versionado e o Hub recebe o broadcast por
`/ws/notifications`. A API REST continua sendo a fonte de verdade após uma
reconexão; não existe replay ou histórico persistido nesta versão.

O canal de e-mail é independente do processamento e usa a porta provider-neutral
`DocumentEmailSender`, com adaptador Web3Forms e retries/DLQ próprios. Uma falha de
WebSocket, RabbitMQ ou Web3Forms não reverte status, contador, retry ou exclusão do
documento. A topologia e a configuração segura estão em
[`document-notifications.md`](document-notifications.md).

## Logs operacionais

Os logs operacionais formam um componente lógico separado dentro do backend. O
`ApplicationLogRecorder` sempre escreve o evento estruturado no logger local e tenta
publicá-lo de forma assíncrona no RabbitMQ; uma falha do broker é registrada no caminho
local e não altera o fluxo de documentos.

A topologia de logs é independente das notificações e do processamento de documentos:
exchange `docflow.application-logs`, fila de persistência
`docflow.application-logs.persist`, retries de 5s/15s/60s, DLX
`docflow.application-logs.dlx` e DLQ `docflow.application-logs.dlq`. Mensagens são
duráveis, usam publisher confirm e só recebem ACK após persistência idempotente em
PostgreSQL. Payloads inválidos ou falhas permanentes seguem para a DLQ.

A tabela `application_logs` é criada pela migration V7 e retém registros por três horas
a partir de `occurred_at`. A API `GET /logs` é read-only, paginada e filtrável; a tela
Angular `/logs` não acessa infraestrutura diretamente e não dispara ações de documento.
Detalhes do contrato HTTP, variáveis e operação estão em
[`api.md`](api.md), [`database.md`](database.md) e
[`../specs/06-deploy/logs-operacionais/deploy.md`](../specs/06-deploy/logs-operacionais/deploy.md).
