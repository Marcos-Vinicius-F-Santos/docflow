# Tarefas — Fase 2: ambiente cloud demonstrável sem custo

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-004  
Dependência: Fase 1 aprovada e composição free tier aprovada por Marcos.

Composição aprovada: Render Free para frontend/backend, Neon Free para PostgreSQL,
CloudAMQP Little Lemur para RabbitMQ e Cloudflare R2 dentro da franquia gratuita para
storage S3-compatible. Detalhes operacionais: `docs/cloud-free-tier.md`.

## Implementação

- [x] F2-T001 Registrar os provedores free tier, limites conhecidos e responsabilidades operacionais aprovados.
- [x] F2-T002 Definir frontend Angular, backend containerizado, PostgreSQL, RabbitMQ e storage S3-compatible como componentes separados.
- [x] F2-T003 Criar configuração de staging com HTTPS gerenciado, URLs geradas e health check do backend.
- [x] F2-T004 Preparar variáveis sensíveis exclusivamente como environment variables/secrets dos provedores.
- [x] F2-T005 Preparar migrations Flyway, buckets, filas e credenciais de menor privilégio.
- [ ] F2-T006 Publicar frontend e conectar a API sem expor infraestrutura interna.
- [ ] F2-T007 Executar fluxo completo de upload, processamento, consulta, download e exclusão fora da máquina local.
- [ ] F2-T008 Documentar e exercitar rollback cloud e retorno de dependências compatíveis.
- [ ] F2-T009 Criar o commit completo da fase com URLs, configuração documentada e evidências.

## Gate da Fase 2

- [ ] FR-004 atendido após publicação externa.
- [ ] AC-005 e AC-006 atendidos após smoke externo.
- [ ] Segredos não aparecem no repositório, bundle ou logs.
- [ ] Rollback foi exercitado.
- [ ] Marcos aprovou o commit e o ambiente.

