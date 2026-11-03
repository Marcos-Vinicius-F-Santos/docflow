# Tarefas — Fase 2: ambiente cloud demonstrável

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisito: FR-004  
Dependência: Fase 1 aprovada e provedor cloud escolhido.

## Implementação

- [ ] F2-T001 Registrar o provedor cloud, regiões, custos, limites, URLs e responsabilidades operacionais aprovados.
- [ ] F2-T002 Definir frontend Angular, backend containerizado, PostgreSQL, RabbitMQ e storage S3-compatible como componentes separados.
- [ ] F2-T003 Criar configuração de staging com HTTPS, domínio/URL e health protegido.
- [ ] F2-T004 Configurar variáveis sensíveis exclusivamente por secrets/environment variables do provedor.
- [ ] F2-T005 Configurar migrations Flyway, buckets/containers, filas e credenciais de menor privilégio.
- [ ] F2-T006 Publicar frontend e conectar a API sem expor infraestrutura interna.
- [ ] F2-T007 Executar fluxo completo de upload, processamento, consulta, download e exclusão fora da máquina local.
- [ ] F2-T008 Documentar e exercitar rollback cloud e retorno de dependências compatíveis.
- [ ] F2-T009 Criar o commit completo da fase com URLs, configuração documentada e evidências.

## Gate da Fase 2

- [ ] FR-004 atendido.
- [ ] AC-005 e AC-006 atendidos.
- [ ] Segredos não aparecem no repositório, bundle ou logs.
- [ ] Rollback foi exercitado.
- [ ] Marcos aprovou o commit e o ambiente.

