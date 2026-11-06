# Tarefas — Fase 4: segurança e multi-tenant

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-006  
Dependência: decisões de identidade, tenant e autorização aprovadas.

## Implementação

- [x] F4-T001 Aprovar provedor JWT/OAuth2, modelo de usuário/tenant e matriz de permissões.
- [x] F4-T002 Mapear endpoints, WebSocket, logs e operações que exigem autenticação/autorização.
- [x] F4-T003 Implementar Spring Security e validação de tokens com configuração externa.
- [x] F4-T004 Criar migration e modelo de `owner_id`/tenant equivalente com estratégia para dados existentes.
- [x] F4-T005 Aplicar isolamento a listagem, detalhe, download, retry, exclusão e logs.
- [x] F4-T006 Implementar perfis `USER`, `OPERATOR` e `ADMIN` conforme matriz aprovada.
- [x] F4-T007 Proteger WebSocket e aplicar rate limiting configurável para upload/retry; CORS existente permanece por ambiente.
- [x] F4-T008 Revisar nomes/tipos de arquivo, auditoria e sanitização de logs.
- [x] F4-T009 Criar testes de acesso anônimo e regressão dos fluxos documentais; validação E2E com dois tenants fica registrada para staging.
- [ ] F4-T010 Executar verificação OWASP e corrigir riscos priorizados. **Adiado para a Fase 9 conforme decisão do projeto.**
- [x] F4-T011 Criar o commit completo da fase após migration, testes e documentação; rollback destrutivo não é executado automaticamente.

## Gate da Fase 4

- [x] FR-006 implementado no backend e no cliente.
- [x] AC-009 e AC-010 cobertos pelo isolamento por tenant e rejeição de acesso anônimo; cenário E2E com dois usuários depende da configuração do staging.
- [x] Estratégia de dados existentes e rollback da migration documentados.
- [x] Nenhum endpoint sensível permanece anônimo sem justificativa aprovada; health, info e métricas permanecem conforme o contrato operacional.
- [x] Commit da fase publicado após a validação local.

