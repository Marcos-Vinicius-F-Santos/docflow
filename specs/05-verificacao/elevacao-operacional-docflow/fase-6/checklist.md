# Checklist de Convergência — Fase 6: contratos e operação

Status: execução validada; fase pronta para o próximo ciclo
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-6/tarefas.md`

## 1. Contratos

- [x] OpenAPI/Swagger representa rotas, payloads, erros e auth.
- [x] Exemplos foram testados contra a API por validação estrutural e suíte backend.
- [x] Readiness/liveness têm semântica distinta.
- [x] Versionamento está documentado.

## 2. Operação

| Item | Resultado | Evidência |
|---|---|---|
| incidente API | [x] | runbook documentado; contrato e probes validados |
| banco | [x] | runbook documentado; conexão usada na suíte e restore |
| RabbitMQ/DLQ | [x] | runbook documentado; integração real na suíte |
| storage | [x] | runbook documentado; integração real na suíte |
| segurança | [x] | runbook e contrato de autenticação documentados |
| backup | [x] | backup PostgreSQL custom-format criado |
| restore | [x] | restaurado em banco isolado; 8 migrations verificadas |
| retenção/órfãos | [x] | política documentada sem limpeza destrutiva automática |
| rollback | [x] | revisão 18 restaurada e template da revisão 19 reativado no staging local |
| diagramas/ADR | [x] | documentos criados e validados |

## 3. Gate

- [x] FR-008 e AC-013/AC-014 rastreados.
- [x] Outra pessoa consegue seguir os runbooks.
- [x] Não há procedimento destrutivo sem alvo e recuperação definidos.
- [x] Aprovação de Marcos obtida para o commit desta fase.

