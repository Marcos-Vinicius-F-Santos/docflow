# Checklist de Convergência — Fase 5: escala, carga e resiliência

Status: não executado  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-5/tarefas.md`

## 1. Cenários

- [ ] Uploads concorrentes.
- [ ] Documentos grandes.
- [ ] Mensagens duplicadas.
- [ ] RabbitMQ indisponível.
- [ ] Storage indisponível.
- [ ] Reinício do backend.
- [ ] Retry após falha.
- [ ] Reconciliação de `RESULT_UNKNOWN`.

## 2. Resultados

| Métrica | Medida | Evidência |
|---|---:|---|
| throughput | não executado | relatório |
| P50 | não executado | relatório |
| P95 | não executado | relatório |
| P99 | não executado | relatório |
| memória | não executado | relatório |
| tempo de fila | não executado | relatório |
| retries | não executado | relatório |
| taxa de erro | não executado | relatório |

## 3. Gate

- [ ] FR-007 e AC-011/AC-012 rastreados.
- [ ] Relatório identifica commit, ambiente, carga, duração e limitações.
- [ ] Duplicação indevida não ocorreu ou foi registrada como falha.
- [ ] Recuperação após reinício foi demonstrada.
- [ ] Aprovação de Marcos obtida.

