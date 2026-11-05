# Checklist de Convergência — Fase 2: ambiente cloud demonstrável

Status: preparação local concluída; publicação externa pendente de criação das contas e recursos free tier.
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-2/tarefas.md`

## 1. Ambiente

- [x] Provedores free tier, custo-alvo zero e limitações estão registrados.
- [ ] Frontend público responde por HTTPS.
- [ ] Backend de staging responde pelo health check e aceita somente a origem do frontend.
- [ ] PostgreSQL, RabbitMQ e storage são externos.
- [x] Secrets são referenciados por environment variables e não estão no repositório ou bundle.
- [ ] Flyway e permissões mínimas foram verificadas nos provedores.

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
- [x] Limitações de custo, disponibilidade, cold start e retenção estão registradas.
- [ ] Aprovação de Marcos obtida.

