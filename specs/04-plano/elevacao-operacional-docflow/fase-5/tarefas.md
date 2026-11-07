# Tarefas — Fase 5: escala, carga e resiliência

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-007  
Dependência: observabilidade da Fase 3 e autenticação da Fase 4 quando aplicável.

## Implementação

- [x] F5-T001 Escolher k6 ou Gatling e registrar versão, ambiente, massa de dados e limites do teste.
- [x] F5-T002 Criar cenários de uploads concorrentes e documentos grandes.
- [x] F5-T003 Criar cenário de mensagens duplicadas e verificar idempotência.
- [x] F5-T004 Criar cenários de RabbitMQ/storage indisponível, reinício do backend e retry.
- [x] F5-T005 Criar cenário de reconciliação de `RESULT_UNKNOWN`.
- [x] F5-T006 Instrumentar coleta de throughput, P50/P95/P99, memória, fila, retries e erro.
- [x] F5-T007 Executar carga em ambiente isolado, com limites para não gerar custo ou dados indevidos.
- [x] F5-T008 Produzir relatório somente com números medidos, limitações e conclusão objetiva.
- [x] F5-T009 Criar o commit completo da fase com scripts, fixtures, relatório e documentação.

## Gate da Fase 5

- [x] FR-007 atendido no staging local.
- [x] AC-011 e AC-012 atendidos no staging local.
- [x] Não há números estimados apresentados como resultado.
- [x] Dados gerados foram identificados e tratados conforme política.
- [x] Marcos aprovou o commit.

