# Checklist de Convergência — Fase 3: observabilidade real

Status: encerrada; dependency scan formalmente transferido para a Fase 9
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
| endpoint Prometheus | [x] | protegido; resposta não autenticada retorna 401 |
| dashboard Grafana | [x] | `ops/grafana/docflow-dashboard.json` |
| DLQ acima do limite | [x] | regra configurada e canal Web3Forms testado |
| crescimento de `PROCESSING` | [x] | regra configurada no Grafana |
| storage/health/HTTP | [x] | regras configuradas; `docflow_health_down` normal após ajuste |
| runbook | [x] | `ops/runbooks/phase-3-observability.md` |

## 2.1 CI e storage de teste

- [x] Testes obrigatórios não dependem mais da imagem pública antiga do MinIO.
- [x] S3Mock `adobe/s3mock:5.2.0` é usado pelo Testcontainers nos testes S3.
- [x] Execução do GitHub Actions confirma a suíte Backend/Testcontainers com S3Mock — run 10.
- [x] Exercício manual com MinIO real não bloqueia a fase; permanece opcional.
- [x] Dependency scan pendente foi transferido para a Fase 9, sem bloquear a observabilidade.

## 3. Gate

- [x] FR-005 e AC-007/AC-008 rastreados em staging externo.
- [x] Alertas não geram ruído por limiar não aprovado no cenário validado.
- [x] Fluxo principal continua funcionando sem depender da persistência de métricas.
- [x] Aprovação de Marcos obtida.

