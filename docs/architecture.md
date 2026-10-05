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
