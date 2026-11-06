# Checklist de Convergência — Fase 4: segurança e multi-tenant

Status: implementação local concluída; validação de staging pendente
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-4/tarefas.md`

## 1. Decisões e schema

- [x] Provedor de identidade e claims aprovados — ADR-010.
- [x] Matriz `USER`/`OPERATOR`/`ADMIN` aprovada — ADR-010.
- [x] Modelo usuário/tenant/owner aprovado — ADR-010.
- [x] Migration e dados existentes testados com Flyway/Testcontainers; rollback destrutivo permanece documentado e não é executado automaticamente.

## 2. Segurança funcional

| Cenário | Resultado | Evidência |
|---|---|---|
| endpoint sensível anônimo | [x] | `DocumentSecurityAccessTest` |
| usuário vê próprio documento | [x] | escopo `tenant_id` no serviço/repositório |
| usuário não vê documento alheio | [x] | consultas por `tenant_id`; E2E de staging pendente |
| mesmo filename isolado | [x] | `tenant_id` obrigatório e índice composto |
| perfis e operações | [x] | `TenantIdentityService` e autorização de retry/exclusão |
| WebSocket protegido | [x] | `SecurityConfiguration` + token de sessão |
| CORS por ambiente | [x] | configuração existente preservada |
| rate limiting | [x] | `DocflowRateLimitingFilter` configurável |
| auditoria sanitizada | [x] | logs persistidos com tenant e sem conteúdo binário |
| OWASP | [ ] | adiado para a Fase 9 |

## 3. Gate

- [x] FR-006 e AC-009/AC-010 rastreados.
- [x] Consultas sensíveis usam escopo de tenant; acesso anônimo é rejeitado quando a proteção está habilitada.
- [x] Fluxo autorizado legado continua funcional com a proteção desabilitada nos testes locais.
- [x] Migration e rollback operacional foram documentados; execução destrutiva depende de aprovação explícita.

