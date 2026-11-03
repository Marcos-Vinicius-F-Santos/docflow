# Tarefas — Fase 5: escala, carga e resiliência

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-007  
Dependência: observabilidade da Fase 3 e autenticação da Fase 4 quando aplicável.

## Implementação

- [ ] F5-T001 Escolher k6 ou Gatling e registrar versão, ambiente, massa de dados e limites do teste.
- [ ] F5-T002 Criar cenários de uploads concorrentes e documentos grandes.
- [ ] F5-T003 Criar cenário de mensagens duplicadas e verificar idempotência.
- [ ] F5-T004 Criar cenários de RabbitMQ/storage indisponível, reinício do backend e retry.
- [ ] F5-T005 Criar cenário de reconciliação de `RESULT_UNKNOWN`.
- [ ] F5-T006 Instrumentar coleta de throughput, P50/P95/P99, memória, fila, retries e erro.
- [ ] F5-T007 Executar carga em ambiente isolado, com limites para não gerar custo ou dados indevidos.
- [ ] F5-T008 Produzir relatório somente com números medidos, limitações e conclusão objetiva.
- [ ] F5-T009 Criar o commit completo da fase com scripts, fixtures, relatório e documentação.

## Gate da Fase 5

- [ ] FR-007 atendido.
- [ ] AC-011 e AC-012 atendidos.
- [ ] Não há números estimados apresentados como resultado.
- [ ] Dados gerados foram identificados e tratados conforme política.
- [ ] Marcos aprovou o commit.

