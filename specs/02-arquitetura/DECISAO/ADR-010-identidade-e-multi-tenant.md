# Registro de Decisão — Identidade e multi-tenant

Data: 2026-11-06  
Status: Aceita — implementação da Fase 4 concluída localmente
Feature: `elevacao-operacional-docflow` / Fase 4

## Contexto

A Fase 4 precisa proteger documentos, logs, WebSocket e operações administrativas,
além de isolar dados entre usuários e tenants. O projeto deve permanecer adequado ao
plano gratuito e compatível com a infraestrutura Neon + Render já configurada.

## Decisão

### Identidade

Usar Neon Auth / Managed Better Auth como provedor de identidade. A validação dos
tokens será integrada ao backend e os secrets permanecerão fora do repositório.

### Modelo de tenant

Usar `tenant_id` como limite principal de isolamento. Usuários serão associados a um
tenant e cada documento, log e operação sensível deverá ser filtrado pelo tenant do
usuário autenticado.

### Perfis

Manter três perfis de aplicação:

- `USER`: opera somente os próprios documentos dentro do tenant;
- `OPERATOR`: executa operações de diagnóstico e retry autorizadas;
- `ADMIN`: administra usuários, tenants e operações globais.

### Dados existentes

Os documentos existentes serão atribuídos a um usuário/tenant bootstrap durante a
migration. Não será mantido acesso anônimo a documentos sem proprietário.

### Superfícies protegidas

Listagem, detalhe, download, retry, exclusão, logs e WebSocket exigirão autenticação e
autorização. O health check permanecerá público para o Render. O endpoint Prometheus
continuará protegido por Basic Auth.

## Fora do escopo desta aprovação

Esta decisão registra a base arquitetural da Fase 4. A migration, a matriz detalhada de
permissões, o isolamento local, o rate limiting e o procedimento de rollback foram
implementados/documentados. A validação E2E remota e a revisão OWASP permanecem fora do
fechamento local e seguem, respectivamente, para o staging e para a Fase 9.

