# Checklist de Convergência — Fase 3: observabilidade real

Status: não executado  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-3/tarefas.md`

## 1. Métricas

- [ ] Upload aceito/concluído/falha.
- [ ] Retry e DLQ.
- [ ] Tempo de upload/processamento e P50/P95 quando aplicável.
- [ ] `PROCESSING` e `RESULT_UNKNOWN`.
- [ ] PostgreSQL, RabbitMQ e storage.
- [ ] Erros HTTP.
- [ ] Correlation ID em logs e eventos.
- [ ] Nenhum segredo, token, conteúdo ou cardinalidade indevida.

## 2. Dashboard e alertas

| Critério | Resultado | Evidência |
|---|---|---|
| endpoint Prometheus | [ ] | resposta |
| dashboard Grafana | [ ] | export/screenshot |
| DLQ acima do limite | [ ] | alerta provocado |
| crescimento de `PROCESSING` | [ ] | alerta |
| storage/health/HTTP | [ ] | alerta |
| runbook | [ ] | documento |

## 3. Gate

- [ ] FR-005 e AC-007/AC-008 rastreados.
- [ ] Alertas não geram ruído por limiar não aprovado.
- [ ] Fluxo principal continua funcionando sem depender da persistência de métricas.
- [ ] Aprovação de Marcos obtida.

