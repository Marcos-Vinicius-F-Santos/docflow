# Topologia RabbitMQ de logs

| Elemento | Nome |
|---|---|
| Exchange | `docflow.application-logs` |
| Fila persistência | `docflow.application-logs.persist` |
| Routing INFO | `application.log.info` |
| Routing WARN | `application.log.warn` |
| Routing ERROR | `application.log.error` |
| Retry 5s | `docflow.application-logs.retry.5s` |
| Retry 15s | `docflow.application-logs.retry.15s` |
| Retry 60s | `docflow.application-logs.retry.60s` |
| DLX | `docflow.application-logs.dlx` |
| DLQ | `docflow.application-logs.dlq` |

Todos os exchanges, filas e mensagens são duráveis/persistentes. A fila principal usa
DLX; as filas de retry usam TTL e encaminhamento para a routing key de persistência.
Publisher confirm é obrigatório. O consumer confirma somente após persistência ou
duplicidade idempotente.
