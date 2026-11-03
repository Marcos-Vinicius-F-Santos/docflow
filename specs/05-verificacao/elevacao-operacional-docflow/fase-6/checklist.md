# Checklist de Convergência — Fase 6: contratos e operação

Status: não executado  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-6/tarefas.md`

## 1. Contratos

- [ ] OpenAPI/Swagger representa rotas, payloads, erros e auth.
- [ ] Exemplos foram testados contra a API.
- [ ] Readiness/liveness têm semântica distinta.
- [ ] Versionamento está documentado.

## 2. Operação

| Item | Resultado | Evidência |
|---|---|---|
| incidente API | [ ] | runbook exercitado |
| banco | [ ] | runbook |
| RabbitMQ/DLQ | [ ] | runbook |
| storage | [ ] | runbook |
| segurança | [ ] | runbook |
| backup | [ ] | backup |
| restore | [ ] | restore verificado |
| retenção/órfãos | [ ] | política/teste |
| rollback | [ ] | execução |
| diagramas/ADR | [ ] | documentos |

## 3. Gate

- [ ] FR-008 e AC-013/AC-014 rastreados.
- [ ] Outra pessoa consegue seguir os runbooks.
- [ ] Não há procedimento destrutivo sem alvo e recuperação definidos.
- [ ] Aprovação de Marcos obtida.

