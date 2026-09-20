# Checklist de verificação — Registro binário e armazenamento assíncrono

A revisão consolidada de convergência está em
`specs/05-verificacao/checklist-convergencia.md`. Este arquivo mantém as evidências
operacionais detalhadas da feature.

Revisão executada em 2026-09-17. Evidências: suíte completa do backend com 73 testes,
incluindo Testcontainers para PostgreSQL, MinIO e RabbitMQ; `git diff --check` sem
erros de whitespace. Este checklist não autoriza deploy por si só.

## API

- [x] `POST /documents` aceita `multipart/form-data` com uma única parte `file`.
- [x] JSON antigo e partes adicionais retornam `400` em `application/problem+json`.
- [x] A resposta de sucesso permanece `201 Created`, com `Location`, `status: PENDING`
      e tamanho real.
- [x] Erros de validação expõem `errorCode` e `invalidField` quando aplicável.

## Fluxo operacional

- [x] O stream é validado, gravado no bucket `docflow-staging` e fechado.
- [x] A ordem observada é staging → persistência `PENDING` → publicação confirmada.
- [x] Falha de confirmação mantém `PENDING` e preserva o staging.
- [x] A mensagem contém somente os seis campos do schema v1.

## RabbitMQ e storage

- [x] Exchange, fila principal, routing key, DLX, DLQ e retries usam os nomes aprovados.
- [x] Mensagens inválidas vão diretamente para a DLQ.
- [x] Falhas transitórias usam exatamente os retries de 5s, 15s e 60s.
- [x] O consumidor obtém claim antes de ler staging e gravar o storage final.
- [x] `exists(objectKey)` evita gravação duplicada.
- [x] O staging só é removido depois da confirmação do storage final.

## Observabilidade e segurança

- [x] Publicação, início, sucesso, falha e disputa de claim incluem `documentId`.
- [x] Nenhum log contém binário, token ou credencial.
- [x] `MINIO_STAGING_BUCKET` e `MINIO_BUCKET` são distintos.
- [x] Nenhuma credencial aparece no código ou no diff versionado.

## Evidência e pendências

- T-020 a T-025 foram aprovadas e marcadas no arquivo de tarefas.
- O caminho ponta a ponta foi exercitado com PostgreSQL, MinIO e RabbitMQ isolados por
  Testcontainers; o resultado foi 73 testes aprovados.
- O smoke test operacional local de T-028 foi executado em 2026-09-17 com credenciais
  efêmeras fora do repositório: health `UP`, `POST /documents` `201`, documento
  consultado como `COMPLETED`, objeto final presente e staging removido após o consumo.
- Não existe ambiente separado de homologação ou produção nesta fase; o Docker Compose
  local é o ambiente operacional aprovado para esta entrega.
- T-026 a T-028 foram marcadas como concluídas no plano; a aprovação de deploy continua
  sendo um gate separado de Marcos.

## Entrega e revisão final

- [x] `./mvnw.cmd -q clean verify` passou com 73 testes, 0 falhas e 0 erros, usando
  Testcontainers para PostgreSQL, MinIO e RabbitMQ.
- [x] Smoke local executado contra o Docker Compose da feature, sem gravar credenciais
  no repositório.
- [x] Revisão do diff contra Spec, plano, arquitetura e tarefas executada; migrations
  da feature não foram alteradas.
- [ ] Aprovação final de Marcos antes do deploy.
