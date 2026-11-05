# Tarefas — Fase 3: observabilidade real

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-005  
Dependência: Fase 2 ou ambiente equivalente aprovado.

## Implementação

- [x] F3-T001 Definir nomes, unidade, cardinalidade e origem das métricas, incluindo limites de alerta.
- [x] F3-T002 Adicionar Micrometer e endpoint Prometheus sem expor segredos ou dados binários.
- [x] F3-T003 Instrumentar upload, processamento, retry, DLQ, storage, RabbitMQ, PostgreSQL e HTTP.
- [x] F3-T004 Propagar correlation ID entre request, banco, mensagem, consumidor, storage e logs.
- [x] F3-T005 Instrumentar documentos `PROCESSING`, `RESULT_UNKNOWN`, duração e erros por dependência.
- [x] F3-T006 Criar dashboard Grafana com painéis de volume, erro, latência, fila e estado.
- [x] F3-T007 Criar alertas para DLQ, `PROCESSING`, storage, P95, health e HTTP.
- [ ] F3-T008 Provocar alerta controlado e escrever runbook de diagnóstico/recuperação. Runbook criado; exercício externo pendente.
- [ ] F3-T009 Criar o commit completo da fase com dashboard, configuração, testes e evidências. Commit será criado após as validações locais.

## Gate da Fase 3

- [ ] FR-005 atendido em staging externo.
- [ ] AC-007 e AC-008 atendidos em staging externo.
- [x] Cardinalidade e sanitização revisadas no código e nos artefatos.
- [x] Dashboard/export, regras e runbook disponíveis; alerta ainda depende da configuração externa.
- [ ] Marcos aprovou o commit.

