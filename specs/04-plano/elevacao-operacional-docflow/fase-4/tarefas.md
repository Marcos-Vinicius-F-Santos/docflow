# Tarefas — Fase 4: segurança e multi-tenant

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-006  
Dependência: decisões de identidade, tenant e autorização aprovadas.

## Implementação

- [x] F4-T001 Aprovar provedor JWT/OAuth2, modelo de usuário/tenant e matriz de permissões. ADR-010 aceita; implementação pendente.
- [ ] F4-T002 Mapear endpoints, WebSocket, logs e operações que exigem autenticação/autorização.
- [ ] F4-T003 Implementar Spring Security e validação de tokens com configuração externa.
- [ ] F4-T004 Criar migration e modelo de `owner_id`/tenant equivalente com estratégia para dados existentes.
- [ ] F4-T005 Aplicar isolamento a listagem, detalhe, download, retry, exclusão e logs.
- [ ] F4-T006 Implementar perfis `USER`, `OPERATOR` e `ADMIN` conforme matriz aprovada.
- [ ] F4-T007 Proteger WebSocket, configurar CORS por ambiente e rate limiting para upload/retry.
- [ ] F4-T008 Revisar nomes/tipos de arquivo, auditoria e sanitização de logs.
- [ ] F4-T009 Criar testes de acesso anônimo, acesso indevido, mesmo filename e privilégios administrativos.
- [ ] F4-T010 Executar verificação OWASP e corrigir riscos priorizados.
- [ ] F4-T011 Criar o commit completo da fase após migration, testes, documentação e rollback serem aprovados.

## Gate da Fase 4

- [ ] FR-006 atendido.
- [ ] AC-009 e AC-010 atendidos.
- [ ] Estratégia de dados existentes e rollback da migration documentados.
- [ ] Nenhum endpoint sensível permanece anônimo sem justificativa aprovada.
- [ ] Marcos aprovou o commit.

