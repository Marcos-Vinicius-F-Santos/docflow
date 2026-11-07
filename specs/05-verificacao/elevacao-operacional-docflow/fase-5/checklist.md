# Checklist de Convergência — Fase 5: escala, carga e resiliência

Status: validação operacional concluída no staging kind; validação autenticada contra o Render permanece opcional e manual.
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-5/tarefas.md`

## 1. Cenários

- [x] Uploads concorrentes — 2 VUs, 30s, 13 uploads aceitos, 0% de erro.
- [x] Documentos grandes — PDF válido de 1 MiB, 2 VUs, 20s, 10 uploads aceitos, 0% de erro.
- [x] Mensagens duplicadas — publicação confirmada pelo RabbitMQ; documento permaneceu único e `COMPLETED`.
- [x] RabbitMQ indisponível — upload respondeu 503 e o serviço foi restaurado.
- [x] Storage indisponível — upload respondeu 500 sem sucesso falso e o serviço foi restaurado.
- [x] Reinício do backend — rollout concluído durante processamento; documento completou.
- [x] Retry após falha — retries observados nos logs após a recuperação do MinIO.
- [x] Reconciliação de `RESULT_UNKNOWN` — documento concluiu após a restauração do MinIO.

## 2. Resultados

| Métrica | Medida | Evidência |
|---|---:|---|
| throughput | 0,395 upload/s (fixture pequeno); 0,490 upload/s (PDF 1 MiB) | `ops/load/k6/phase-5-preflight.md` |
| P50 | 49 ms (fixture pequeno); 3,25 ms HTTP (PDF 1 MiB) | `ops/load/k6/phase-5-preflight.md` |
| P95 | 1,23 s (fixture pequeno); 45,21 ms HTTP (PDF 1 MiB) | `ops/load/k6/phase-5-preflight.md` |
| P99 | 1,23 s (fixture pequeno); 74,76 ms HTTP (PDF 1 MiB) | `ops/load/k6/phase-5-preflight.md` |
| memória | amostra temporal sob carga: backend 321–324 MiB, RabbitMQ 144–197 MiB, Postgres 63 MiB e MinIO 83 MiB | `ops/load/k6/phase-5-preflight.md` |
| tempo de fila | filas de storage zeradas ao final; `kubectl top` disponível via Metrics Server | `ops/load/k6/phase-5-preflight.md` |
| retries | 3 agendamentos observados no cenário de MinIO indisponível | `ops/load/k6/phase-5-preflight.md` |
| taxa de erro | 0% nos cenários válidos | `ops/load/k6/phase-5-preflight.md` |
| suíte completa do backend | 256 testes, 0 falhas, 0 erros, 0 ignorados | relatórios `backend/target/surefire-reports` |

## 3. Gate

- [x] FR-007 e AC-011/AC-012 rastreados no staging local.
- [x] Relatório identifica ambiente, carga, duração, medições e limitações.
- [x] Duplicação indevida não ocorreu ou foi registrada como falha.
- [x] Recuperação após reinício foi demonstrada.
- [x] Contrato protegido validado no staging com JWKS efêmero e upload autenticado: `201`; limpeza: `204`.
- [x] Aprovação de Marcos obtida para o commit desta fase.

