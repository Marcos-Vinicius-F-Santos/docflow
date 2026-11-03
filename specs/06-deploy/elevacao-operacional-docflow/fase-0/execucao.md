# Execução — Fase 0: estabilização e baseline

Ambiente-alvo: checkout local em `E:\Projects\docflow`  
Status: concluída localmente; commit único da fase em preparação, sem push ou deploy.

## Pré-condições

- [x] Docker Desktop e daemon Linux acessíveis; Docker Desktop 4.97.0, engine 29.9.2.
- [x] Java 21, Node/npm e Docker Compose disponíveis; Node 24.19.0, npm 12.0.2 e
  Docker CLI 29.9.2 foram identificados.
- [x] `git status --short` revisado.
- [x] O ref-base `bb908044d5032751c4ef2d2403fcb6b3fdc33e73` foi registrado; o hash do
  commit da fase será conferido no histórico após a criação.

## Passos

1. Registrar `git rev-parse HEAD`, `git status --short` e versões de Java, Node, npm e Docker.
2. Executar `npm ci`, lint, testes e build em `frontend/`.
3. Executar `./mvnw verify` em `backend/` com Docker disponível.
4. Repetir a execução limpa quando uma correção for aplicada.
5. Registrar quantidade de testes, cobertura, duração, warnings e limitações.
6. Atualizar README e checklist sem declarar validação CI ou deploy.
7. Gerar o commit da fase após aprovação.

## Rollback

Antes de qualquer correção, preservar o commit anterior e os logs. Em caso de regressão,
retornar ao commit anterior por revisão explícita; não usar reset destrutivo nem remover
volumes Docker.

## Registro desta execução

- Frontend: `npm ci --no-audit --no-fund`, lint, 110 testes e build passaram.
- Backend unitário: 211 testes passaram sem falhas ou erros.
- Backend completo: `./mvnw.cmd -B verify` passou com 250 testes, 0 falhas, 0 erros e
  0 ignorados, usando PostgreSQL, RabbitMQ e MinIO via Testcontainers em 1 min 38 s.
- O build inicialmente reportou `hub-page.scss` com 5,67 kB contra o orçamento de
  aviso de 4 kB. A correção consolidou estilos e ajustou explicitamente o aviso para
  6 kB, mantendo o erro em 8 kB; o build seguinte passou sem alerta.
- O primeiro comando de teste frontend usou a opção inválida `--run`; ele foi repetido
  com o comando oficial `npm test -- --watch=false` antes de registrar o resultado.
- Não houve push, deploy externo, remoção de volumes ou alteração destrutiva de dados.
- Evidências detalhadas: `specs/05-verificacao/elevacao-operacional-docflow/fase-0/baseline.md`.

## Próximos passos após o commit

1. Manter o commit anterior como referência de rollback.
2. Revisar o `git show --stat` do commit único da fase.
3. Prosseguir para a Fase 1 somente após a revisão do commit; não fazer deploy externo
   como parte desta execução.

## Resultado obrigatório

Relatório de baseline, logs dos comandos, checklist preenchido e hash do commit aprovado.

