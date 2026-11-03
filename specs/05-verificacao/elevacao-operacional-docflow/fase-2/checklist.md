# Checklist de Convergência — Fase 2: ambiente cloud demonstrável

Status: não executado  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-2/tarefas.md`

## 1. Ambiente

- [ ] Provedor, região, custos e limites estão registrados.
- [ ] Frontend público responde por HTTPS.
- [ ] Backend de staging está protegido.
- [ ] PostgreSQL, RabbitMQ e storage são externos.
- [ ] Secrets não estão no repositório ou no bundle.
- [ ] Flyway e permissões mínimas foram verificadas.

## 2. Fluxo e falha

| Critério | Resultado | Evidência |
|---|---|---|
| upload externo | [ ] | URL/documentId |
| processamento RabbitMQ | [ ] | estado/log |
| storage externo | [ ] | verificação |
| consulta e download | [ ] | smoke |
| exclusão | [ ] | smoke |
| configuração ausente falha explicitamente | [ ] | log sanitizado |
| rollback cloud | [ ] | execução |

## 3. Gate

- [ ] FR-004 e AC-005/AC-006 rastreados.
- [ ] Não há dependência acidental de `localhost`.
- [ ] Limitações de custo, disponibilidade e retenção registradas.
- [ ] Aprovação de Marcos obtida.

