# Checklist de Convergência — Fase 1: CI/CD próprio do DocFlow

Status: execução parcial — target Kubernetes local validado; deploy remoto aguardando cluster
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-1/tarefas.md`

## 1. Pipeline

- [x] Pull request dispara CI.
- [x] Push na branch principal dispara CI.
- [x] Maven/Testcontainers estão configurados no job de backend.
- [x] npm/lint/testes/build estão configurados no job de frontend.
- [x] Imagem é construída e escaneada.
- [x] Bundle é verificado contra segredos.
- [x] Badge aponta para o workflow correto.

## 2. Deploy e smoke

| Critério | Resultado | Evidência |
|---|---|---|
| imagem versionada pelo commit | [~] | Imagem construída e escaneada localmente; workflow publica `ghcr.io/<owner>/docflow:<commit-sha>` após push em `main`; execução GHCR ainda pendente. |
| Flyway | [~] | 7 migrations validadas pela suíte Maven/Testcontainers; validação no staging aguarda target |
| `/actuator/health` | [~] | smoke local passou; execução remota aguarda target de staging |
| upload real | [~] | smoke local passou; execução remota aguarda target de staging |
| processamento RabbitMQ | [~] | smoke local chegou a `COMPLETED`; execução remota aguarda target |
| storage final | [~] | smoke local confirmou download do conteúdo; execução remota aguarda target |
| consulta/download/exclusão | [~] | smoke local passou; execução remota aguarda target de staging |
| rollback | [~] | `kubectl rollout undo` validado no cluster kind local; rollback remoto por digest aguarda cluster |

## 3. Rastreabilidade e gate

- [~] FR-002 → F1-T001 a F1-T003; implementação local pronta, execução no GitHub pendente.
- [~] FR-003 → F1-T004 a F1-T008; GHCR e smoke implementados, deploy/rollback pendentes.
- [ ] AC-003 e AC-004 confirmados.
- [x] Falhas e limitações registradas.
- [ ] Commit e workflow foram revisados por Marcos.

## 4. Evidências locais — 2026-11-04

- Backend: `./mvnw -B verify` passou com 250 testes, 0 falhas e 0 erros.
- Frontend: lint, 110 testes e build passaram após atualização do Angular para `22.2.1`.
- Segurança: scan de dependências e scan do sistema operacional da imagem passaram sem
  vulnerabilidades HIGH/CRITICAL; o primeiro scan havia encontrado quatro achados, todos
  corrigidos (Angular Router e PostgreSQL JDBC).
- Imagem: build Docker passou; inspeção confirmou usuário `docflow`, porta `8080` e
  health check do Actuator.
- Sintaxe: `actionlint` passou para o workflow e `bash -n` passou para o smoke test.
- Kubernetes local: cluster `kind-docflow-staging` criado, manifests renderizados e deploy
  validado com PostgreSQL, RabbitMQ, MinIO, bootstrap e backend.
- Smoke via `kubectl port-forward`: health `UP`, upload, processamento `COMPLETED`,
  download verificado e exclusão HTTP `204`.
- Rollback local validado com `kubectl rollout undo deployment/docflow-backend`; rollout
  posterior ficou saudável.

