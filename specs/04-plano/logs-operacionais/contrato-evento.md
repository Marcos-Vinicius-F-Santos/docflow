# Contrato `ApplicationLogEvent` v1

O evento interno é JSON, versionado e publicado somente pelo backend.

```json
{
  "eventId": "00000000-0000-0000-0000-000000000000",
  "schemaVersion": 1,
  "occurredAt": "2026-10-29T12:00:00Z",
  "level": "ERROR",
  "service": "document-api",
  "component": "document-storage-consumer",
  "operation": "document.processing",
  "message": "Falha ao confirmar o storage final",
  "correlationId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "details": {
    "errorCode": "STORAGE_UNAVAILABLE"
  }
}
```

Regras:

- `eventId`, `schemaVersion`, `occurredAt`, `level`, `service`, `component`,
  `operation` e `message` são obrigatórios.
- `correlationId`, `documentId` e `details` são opcionais; UUIDs inválidos não são
  aceitos.
- `level` aceita somente `INFO`, `WARN` e `ERROR`.
- `details` é um objeto JSON limitado a dados escalares/estruturados sanitizados.
- Senhas, API keys, tokens, credenciais, buckets, conteúdo binário e stack traces não
  sanitizados são proibidos no payload.
- O evento é imutável após criação e o consumidor usa `eventId` para idempotência.
