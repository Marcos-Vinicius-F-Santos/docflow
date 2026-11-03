# Persistência e retenção de logs

A migration seguinte à V6 criará `application_logs` com:

| Coluna | Regra |
|---|---|
| `event_id` | UUID, obrigatório, único |
| `schema_version` | inteiro, obrigatório |
| `occurred_at` | timestamp com timezone, obrigatório |
| `level`, `service`, `component`, `operation`, `message` | obrigatórios |
| `correlation_id`, `document_id`, `details_json` | opcionais |
| `persisted_at` | timestamp com timezone, obrigatório |

Índices: `occurred_at`, `level`, `service`, `document_id` e `correlation_id`.

A retenção remove somente registros com `occurred_at < now - 3 hours`. O clock do
scheduler será injetável para testes; a operação será idempotente e não alterará
documentos nem logs locais.
