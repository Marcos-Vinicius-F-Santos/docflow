# Matriz de requisito da vaga → evidência real

Feature: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Fase: 6 — contratos e operação  
Status: evidências locais validadas; publicação externa e segurança aprofundada ficam para as fases previstas.

| Requisito demonstrável | Evidência real no repositório/ambiente | Estado |
|---|---|---|
| README e execução local | `README.md`, `infra/kubernetes/local/README.md`, cluster `kind-docflow-staging` | Validado |
| Frontend | `frontend/`, testes e build/lint registrados nas fases anteriores | Validado anteriormente |
| Backend/API | `backend/`, suíte Maven: 256 testes, 0 falhas, 0 erros, 0 skips | Validado |
| CI | `.github/workflows/`, execução do workflow registrada nas evidências anteriores | Validado anteriormente |
| Imagem/deploy | `backend/Dockerfile`, imagem `docflow-backend:k8s-local`, rollout concluído no kind | Validado |
| Dashboard/observabilidade | `ops/`, métricas Prometheus e dashboards Grafana documentados nas fases anteriores | Validado anteriormente |
| Carga | `ops/load/` e evidências da Fase 5 | Validado anteriormente |
| Segurança | autenticação Bearer no contrato OpenAPI, runbook e regras existentes; revisão aprofundada prevista na Fase 9 | Parcial, conforme escopo |
| Contrato HTTP | `docs/openapi.yaml` e `docs/api.md`; rotas em lote explicitamente marcadas como planejadas | Validado |
| Diagnóstico operacional | `docs/runbooks/fase-6-operacao.md` | Validado |
| Liveness/readiness | `application.properties`, probes Kubernetes e respostas HTTP 200 no staging local | Validado |
| Backup/restore | `scripts/postgres-backup.ps1`, `scripts/postgres-restore.ps1`; restore isolado com 8 migrations | Validado |
| Retenção/órfãos | política documentada no runbook e ADR-012; sem limpeza automática destrutiva | Validado |
| Rollback | revisão 18 restaurada e template da revisão 19 reativado no staging local | Validado |
| Diagramas e decisões | `docs/diagrams/architecture.mmd`, `docs/diagrams/async-flow.mmd`, ADR-012 | Validado |

## Limites da evidência

- A matriz não transforma uma capacidade planejada em capacidade implementada.
- Os endpoints batch continuam fora da superfície OpenAPI até controllers e testes serem entregues.
- O rollback foi executado apenas no staging local e a revisão validada foi reativada.
- A aprovação do commit da fase depende de revisão explícita do responsável pelo projeto.
