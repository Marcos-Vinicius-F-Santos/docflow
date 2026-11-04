# CI/CD e staging

## CI implementado

O workflow [`ci.yml`](../.github/workflows/ci.yml) executa em pull requests e em
pushes na `main`:

- `./mvnw -B verify`, incluindo Testcontainers;
- `npm ci`, lint, testes e build do frontend;
- verificação de segredos no bundle Angular;
- scan de dependências e da imagem com Trivy;
- build do backend a partir de `backend/Dockerfile`;
- publicação no GHCR somente após CI verde em `main`.

Em `main`, a imagem recebe duas tags: o SHA completo do commit e `main`. O SHA é a
referência reprodutível para deploy e rollback; `main` é apenas uma conveniência para
inspeção do estado mais recente.

O scan de manifests cobre bibliotecas de aplicação; o scan da imagem cobre o sistema
operacional. Em ambos os casos o workflow falha para vulnerabilidades HIGH ou CRITICAL
não corrigidas.

## Segredos e permissões

O workflow usa somente o `GITHUB_TOKEN` efêmero fornecido pelo GitHub Actions para
publicar no GHCR. O job de publicação declara `packages: write`; os demais jobs usam
somente `contents: read`. Nenhuma credencial de aplicação, banco, RabbitMQ ou storage
é armazenada no repositório ou na imagem.

## Smoke test

O script [`staging-smoke-test.sh`](../scripts/staging-smoke-test.sh) recebe a URL do
backend pela variável `DOCFLOW_BASE_URL` e verifica health, upload, processamento,
download, conteúdo baixado e exclusão. Ele não imprime credenciais nem o conteúdo do
documento.

Exemplo local, com o backend já iniciado:

```bash
DOCFLOW_BASE_URL=http://localhost:8080 ./scripts/staging-smoke-test.sh
```

## Kubernetes local e deploy remoto

O target local da Fase 1 usa `kind` no Docker Desktop. Os manifests estão em
[`infra/kubernetes/local`](../infra/kubernetes/local) e o deploy reproduzível está em
[`k8s-local-deploy.ps1`](../scripts/k8s-local-deploy.ps1). O script constrói/carrega as
imagens, cria o Secret a partir de `infra/.env`, aplica PostgreSQL, RabbitMQ, MinIO,
bootstrap e backend, e aguarda os rollouts.

O smoke test foi executado via `kubectl port-forward` contra esse cluster local.
O rollback local foi validado com `kubectl rollout undo`; em um cluster remoto, o
procedimento deverá promover a imagem anterior pelo digest registrado.

O deploy remoto e rollback automatizado permanecem pendentes

A implementação do workflow de deploy permanece bloqueada até a confirmação do
provedor/target de staging. A spec deixou essa escolha aberta; não foi adotado um
provedor SSH, cloud ou serviço gerenciado por suposição.

Quando o target for confirmado, o workflow deverá:

1. usar a imagem `ghcr.io/<owner>/docflow:<commit-sha>` produzida pelo CI verde;
2. carregar os segredos no ambiente `staging` do GitHub, fora do repositório;
3. aplicar Flyway e aguardar `/actuator/health`;
4. executar o smoke test completo;
5. registrar commit, tag, digest, URL e resultado;
6. promover a imagem anterior por digest em caso de rollback e registrar o resultado.

As validações locais da implementação estão registradas na checklist da Fase 1. Ainda
não há execução do workflow no GitHub, publicação no GHCR ou smoke test remoto.
