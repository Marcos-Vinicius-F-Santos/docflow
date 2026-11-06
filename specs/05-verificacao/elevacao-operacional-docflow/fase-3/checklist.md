# Checklist de Convergência — Fase 3: observabilidade real

Status: correção operacional aplicada; validação do CI pendente
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-3/tarefas.md`

## 1. Métricas

- [x] Upload aceito/concluído/falha.
- [x] Retry e DLQ.
- [x] Tempo de upload/processamento e P50/P95 quando aplicável.
- [x] `PROCESSING` e `RESULT_UNKNOWN`.
- [x] PostgreSQL, RabbitMQ e storage.
- [x] Erros HTTP via métricas HTTP do Spring.
- [x] Correlation ID em logs e eventos.
- [x] Nenhum segredo, token, conteúdo ou cardinalidade indevida.

## 2. Dashboard e alertas

| Critério | Resultado | Evidência |
|---|---|---|
| endpoint Prometheus | [ ] | protegido no código; resposta externa pendente |
| dashboard Grafana | [x] | `ops/grafana/docflow-dashboard.json` |
| DLQ acima do limite | [ ] | alerta provocado |
| crescimento de `PROCESSING` | [ ] | alerta |
| storage/health/HTTP | [ ] | alerta |
| runbook | [x] | `ops/runbooks/phase-3-observability.md` |

## 2.1 CI e storage de teste

- [x] Testes obrigatórios não dependem mais da imagem pública antiga do MinIO.
- [x] S3Mock `adobe/s3mock:5.2.0` é usado pelo Testcontainers nos testes S3.
- [ ] Execução do GitHub Actions confirma a suíte Backend/Testcontainers com S3Mock.
- [ ] Exercício manual com MinIO real, incluindo rejeição de credenciais, quando necessário.

## 3. Gate

- [ ] FR-005 e AC-007/AC-008 rastreados em staging externo.
- [ ] Alertas não geram ruído por limiar não aprovado.
- [x] Fluxo principal continua funcionando sem depender da persistência de métricas.
- [ ] Aprovação de Marcos obtida.

