# Execução — Fase 1: CI/CD próprio do DocFlow

Ambiente-alvo: GitHub Actions, GHCR e Kubernetes local via `kind`.
Status: execução parcial; CI, imagem, manifests Kubernetes e smoke local implementados. Deploy remoto aguardando cluster acessível pelo GitHub.

## Pré-condições

- [x] Commit da Fase 0 aprovado: `97e0ada0d1509483afed4ab44d352588c97270f9`.
- [ ] Secrets do GitHub configurados sem valores no repositório.
- [~] GHCR será usado pelo workflow; Kubernetes local foi confirmado e o cluster remoto ainda não foi preparado.
- [ ] Permissão de publicação e deploy revisada.
- [ ] Rollback da imagem anterior identificável.

## Passos

1. Abrir pull request com o commit da fase.
2. Aguardar CI verde e salvar URL da execução.
3. Confirmar tag da imagem pelo SHA e guardar digest.
4. Executar deploy de staging somente pelo workflow aprovado.
5. Conferir Flyway, health, smoke de upload/processamento/consulta/download/exclusão.
6. Registrar logs, imagem, commit e URL do ambiente.
7. Executar rollback controlado para a imagem anterior e registrar resultado.
8. Reaplicar a versão aprovada somente após validação e autorização.

## Rollback

Promover a imagem anterior pelo digest registrado. Se o schema tiver migration incompatível,
parar e usar o procedimento de rollback da migration; não editar o banco manualmente.

## Registro da execução inicial

- `.github/workflows/ci.yml` criado para PR e push em `main`.
- Jobs definidos para Maven/Testcontainers, frontend, scan de dependências, scan de
  segredos no bundle e build/scan/publicação da imagem no GHCR.
- `backend/Dockerfile` criado com configuração externa, usuário não-root e health check.
- `scripts/staging-smoke-test.sh` criado para health, upload, processamento,
  consulta, download e exclusão.
- O workflow de deploy ainda não foi criado porque o provedor/target de staging é uma
  decisão aberta da spec e mudaria o mecanismo de publicação/rollback.
- Nenhum segredo foi criado, armazenado ou publicado nesta execução.

## Evidências locais — 2026-11-04

- `backend/mvnw -B verify`: sucesso, 250 testes, 0 falhas e 0 erros, com Testcontainers.
- Frontend: lint, 110 testes e build concluídos com sucesso.
- `docker build` da imagem do backend: sucesso; usuário não-root, porta `8080` e
  health check confirmados por inspeção.
- Trivy nos manifests e no sistema operacional da imagem: 0 vulnerabilidades
  HIGH/CRITICAL após atualizar Angular para `22.2.1` e PostgreSQL JDBC para `42.7.12`.
- Smoke test remoto e publicação no GHCR não foram executados porque ainda não existe
  target de staging confirmado nem execução do workflow no GitHub. O smoke test local
  passou com health `UP`, upload, processamento até `COMPLETED`, download verificado
  byte a byte e exclusão com HTTP `204`.
- Cluster Kubernetes local `kind-docflow-staging` criado e validado com o script
  `scripts/k8s-local-deploy.ps1`; todos os pods de PostgreSQL, RabbitMQ, MinIO,
  bootstrap e backend ficaram saudáveis.
- Smoke executado através de `kubectl port-forward` contra o backend dentro do cluster,
  com resultado aprovado.
- `kubectl rollout undo deployment/docflow-backend` validado localmente e novo rollout
  concluído com o pod saudável.

## Resultado obrigatório

URL de CI, URL/digest da imagem, log de smoke, log de rollback e checklist aprovado.

