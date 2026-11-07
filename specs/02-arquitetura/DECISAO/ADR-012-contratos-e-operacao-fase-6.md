# ADR-012 — Contratos e operação da Fase 6

## Status

Aceito para a execução local/staging; produção permanece sujeita à aprovação do
deploy correspondente.

## Decisões

1. O contrato REST implementado é versionado como `1.0.0` em
   [`docs/openapi.yaml`](../../../docs/openapi.yaml), sem inventar rotas que não
   existem nos controllers.
2. Liveness mede somente o processo. Readiness mede o estado de prontidão, PostgreSQL
   e RabbitMQ. MinIO permanece uma dependência transacional tratada por operação, não
   uma condição global de readiness.
3. Backups PostgreSQL usam custom-format e restore somente em banco separado. O script
   recusa sobrescrever o banco padrão.
4. Retenção de logs permanece em três horas. Limpeza de objetos órfãos não é automática
   até que exista inventário seguro e dry-run.
5. Rollback mantém migrations expand-only, banco, filas e objetos; somente a imagem do
   backend é revertida sem apagar evidências.

## Consequências

- O OpenAPI e `docs/api.md` são revisados junto com controllers.
- A indisponibilidade do MinIO não provoca flapping do Deployment.
- O restore é comprovável sem comprometer o banco original.
- Fixtures de operações em lote permanecem planejadas até implementação real.
