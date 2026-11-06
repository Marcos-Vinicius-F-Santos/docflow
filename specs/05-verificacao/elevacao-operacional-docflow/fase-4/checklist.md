# Checklist de Convergência — Fase 4: segurança e multi-tenant

Status: decisões aprovadas; implementação não iniciada  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-4/tarefas.md`

## 1. Decisões e schema

- [x] Provedor de identidade e claims aprovados — ADR-010.
- [x] Matriz `USER`/`OPERATOR`/`ADMIN` aprovada — ADR-010.
- [x] Modelo usuário/tenant/owner aprovado — ADR-010.
- [ ] Migration, dados existentes e rollback testados.

## 2. Segurança funcional

| Cenário | Resultado | Evidência |
|---|---|---|
| endpoint sensível anônimo | [ ] | teste |
| usuário vê próprio documento | [ ] | teste |
| usuário não vê documento alheio | [ ] | teste |
| mesmo filename isolado | [ ] | teste |
| perfis e operações | [ ] | matriz/testes |
| WebSocket protegido | [ ] | teste |
| CORS por ambiente | [ ] | configuração/teste |
| rate limiting | [ ] | teste |
| auditoria sanitizada | [ ] | evento/log |
| OWASP | [ ] | relatório |

## 3. Gate

- [ ] FR-006 e AC-009/AC-010 rastreados.
- [ ] Não há vazamento de metadados em `404`/`403`.
- [ ] Fluxo autorizado continua funcional.
- [ ] Rollback e migration foram revisados por Marcos.

