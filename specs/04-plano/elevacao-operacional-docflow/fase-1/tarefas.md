# Tarefas — Fase 1: CI/CD próprio do DocFlow

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisitos: FR-002, FR-003  
Dependência: Fase 0 aprovada.

## Implementação

- [x] F1-T001 Mapear comandos aprovados na Fase 0 para jobs reproduzíveis de CI.
- [x] F1-T002 Criar `.github/workflows/ci.yml` para backend, Testcontainers, frontend, build de imagem, scan e verificação de segredos.
  - Verificação: cada job falha com código não zero quando sua etapa falha.
- [x] F1-T003 Criar Dockerfile do backend e validar execução sem segredos embutidos.
  - Verificação: a imagem inicia com configuração externa e passa health check.
- [x] F1-T004 Configurar publicação versionada no GHCR por commit, com permissões mínimas.
  - Verificação: uma imagem identificável pelo SHA é publicada após CI verde.
- [ ] F1-T005 Criar `.github/workflows/deploy-staging.yml` condicionado ao CI verde.
  - Target local Kubernetes via `kind` foi validado; workflow remoto permanece pendente de um cluster acessível pelo GitHub.
- [x] F1-T006 Implementar smoke test de health, upload, RabbitMQ, storage, consulta, download e exclusão.
- [x] F1-T007 Documentar rollback para a imagem anterior e registrar commit, imagem, execução e resultado.
- [x] F1-T008 Adicionar badge e links de evidência ao README.
- [ ] F1-T009 Criar o commit completo da Fase 1 após CI, smoke e rollback serem validados.

## Gate da Fase 1

- [ ] FR-002 e FR-003 atendidos.
- [ ] AC-003 e AC-004 atendidos.
- [ ] Nenhum segredo aparece em logs, bundle ou imagem.
- [ ] Marcos aprovou o commit e o primeiro deploy de staging.

