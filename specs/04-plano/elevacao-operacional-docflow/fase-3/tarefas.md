# Tarefas — Fase 3: observabilidade real

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-005  
Dependência: Fase 2 ou ambiente equivalente aprovado.

## Implementação

- [ ] F3-T001 Definir nomes, unidade, cardinalidade e origem das métricas, incluindo limites de alerta.
- [ ] F3-T002 Adicionar Micrometer e endpoint Prometheus sem expor segredos ou dados binários.
- [ ] F3-T003 Instrumentar upload, processamento, retry, DLQ, storage, RabbitMQ, PostgreSQL e HTTP.
- [ ] F3-T004 Propagar correlation ID entre request, banco, mensagem, consumidor, storage e logs.
- [ ] F3-T005 Instrumentar documentos `PROCESSING`, `RESULT_UNKNOWN`, duração e erros por dependência.
- [ ] F3-T006 Criar dashboard Grafana com painéis de volume, erro, latência, fila e estado.
- [ ] F3-T007 Criar alertas para DLQ, `PROCESSING`, storage, P95, health e HTTP.
- [ ] F3-T008 Provocar alerta controlado e escrever runbook de diagnóstico/recuperação.
- [ ] F3-T009 Criar o commit completo da fase com dashboard, configuração, testes e evidências.

## Gate da Fase 3

- [ ] FR-005 atendido.
- [ ] AC-007 e AC-008 atendidos.
- [ ] Cardinalidade e sanitização revisadas.
- [ ] Dashboard/export, alerta e runbook disponíveis.
- [ ] Marcos aprovou o commit.

