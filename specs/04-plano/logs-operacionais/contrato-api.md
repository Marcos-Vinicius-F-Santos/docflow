# Contrato inicial `GET /logs`

Endpoint read-only:

```text
GET /logs?page=0&size=50&level=ERROR&service=document-api
  &component=document-storage-consumer&operation=document.processing
  &documentId={uuid}&correlationId={uuid}
  &occurredFrom=2026-10-29T00:00:00Z&occurredTo=2026-10-29T23:59:59Z
```

Parâmetros:

| Parâmetro | Tipo | Regra |
|---|---|---|
| `page` | inteiro | padrão `0`, não negativo |
| `size` | inteiro | padrão `50`, mínimo `1`, máximo `100` |
| `level` | enum | `INFO`, `WARN` ou `ERROR` |
| `service` | texto | igualdade, opcional |
| `component` | texto | igualdade, opcional |
| `operation` | texto | igualdade, opcional |
| `documentId` | UUID | igualdade, opcional |
| `correlationId` | UUID | igualdade, opcional |
| `occurredFrom` | ISO-8601 | inclusivo, opcional |
| `occurredTo` | ISO-8601 | inclusivo, opcional; não pode preceder `occurredFrom` |

A resposta é `200` com itens ordenados por `occurredAt` decrescente e `eventId`
decrescente como desempate:

```json
{
  "items": [],
  "page": 0,
  "size": 50,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Filtros inválidos retornam `400` em `ProblemDetail`; indisponibilidade da persistência
retorna `503` sem expor SQL, stack trace ou credenciais.
