# Checklist de Convergência

> Este diretório contém evidências de validação, não é fonte primária de comportamento.
> Para o contrato funcional use `specs/03-features/backend-operacional-docflow/spec.md`;
> para limites use `specs/02-arquitetura/ARQUITETURA.md` e os ADRs.

Feature: `registro-binario-e-armazenamento-assincrono-documentos`

Data da revisão: 2026-09-19

Status: revisão de convergência concluída; deploy não aprovado.

> A seção histórica abaixo registra a revisão de 2026-09-17 da feature anterior. Para a
> rodada vigente, a evidência consolidada do backend operacional está na seção seguinte;
> o comportamento continua tendo como fontes primárias a Spec da feature, a arquitetura
> e os ADRs, não este checklist.

## Rodada vigente — backend operacional do DocFlow

Data: 2026-09-19

- [x] T-015: testes unitários cobrem `exists=true`, tentativas restantes, backoff de
  `1m/5m/15m/30m/60m`, quinta tentativa em `FAILED`, falhas permanentes e timeout
  MinIO convertido para `RESULT_UNKNOWN`.
- [x] T-016: PostgreSQL/Testcontainers validou migration V1–V5, claim exclusivo,
  persistência da próxima elegibilidade e takeover após a lease de 10 segundos.
- [~] T-017: RabbitMQ/Testcontainers validou publisher confirm, exchange, fila
  principal, retry com TTL de `5s`, retorno à fila principal e DLQ direta; os TTLs
  reais de `15s` e `60s` permanecem cobertos pela configuração/testes unitários, sem
  espera integral nesta rodada, e `ack`/`nack` foram observados no handler com canal
  controlado, não por um listener completo.
- [~] T-018: MinIO/Testcontainers validou staging, leitura, storage final, remoção e
  rejeição de credenciais inválidas; o bootstrap Compose foi executado com sucesso e
  de forma idempotente em validação operacional separada. O timeout real bloqueado
  continua coberto por teste controlado, não por uma espera física de 3 segundos.
- [~] T-019: PostgreSQL + MinIO reais validaram duas chamadas concorrentes para o mesmo
  documento, uma única gravação final e reconhecimento idempotente da duplicata; o
  teste não publica duas mensagens nem inicia dois listeners RabbitMQ reais porque a
  configuração aprovada é consumidor único com concorrência `1..1`.
- [x] T-020: `./mvnw.cmd test` passou com 92 testes, 0 falhas e 0 erros.

Essas linhas são evidência da rodada atual; T-015 a T-023 foram aprovadas por Marcos e
estão marcadas como concluídas em `specs/04-plano/backend-operacional-docflow/tarefas.md`.
Essa aprovação das tarefas não aprova o deploy.

### Rastreabilidade da entrega

- [x] FR-001: os testes unitários usam mocks; os testes PostgreSQL-only desabilitam a
  mensageria; os testes que exigem broker/storage iniciam dependências próprias. A
  suíte completa não tentou usar um RabbitMQ externo em `localhost:5672`.
- [~] FR-002: `.env.example`, Compose, bootstrap idempotente, buckets separados,
  diagnóstico de volumes e smoke test estão documentados; `docker compose config --quiet`
  passou com o exemplo. Nesta revisão não foi repetida uma execução independente do
  bootstrap a partir de checkout limpo.
- [x] FR-003 a FR-005: claim atômico, scheduler, backoff, `RESULT_UNKNOWN` e transições
  permanentes estão implementados e cobertos pelos testes unitários e PostgreSQL reais
  listados acima.
- [~] FR-006: o cliente MinIO configura `connect/read/write timeout` e `callTimeout` de
  3 segundos, e testes controlados traduzem `IOException` para `RESULT_UNKNOWN`; não
  há deadline única envolvendo todo o processamento da entrega RabbitMQ.
- [~] FR-007 e FR-008: topologia, publisher confirm, retries, DLQ e requeue estão
  implementados e parcialmente validados em RabbitMQ real. Os TTLs de `15s` e `60s`,
  falha real de publicação do retry e o ciclo completo de `ack`/`nack` do listener não
  foram observados em broker real.
- [~] FR-009 e FR-010: consumidor, `prefetch=1` e concorrência `1..1` estão declarados
  explicitamente e o claim/storage idempotente foi validado com PostgreSQL + MinIO;
  não há teste que publique mensagens duplicadas em RabbitMQ e execute dois listeners.
- [~] FR-011: os documentos oficiais atuais estão majoritariamente alinhados, mas
  `ARQUITETURA.md` contém um link para o caminho antigo da Spec consolidada e o README
  ainda aponta para uma revisão histórica que contém afirmações antigas, embora esteja
  rotulada como histórica.
- [~] AC-001 a AC-009: cada critério de aceite possui implementação e evidência de
  teste identificáveis na seção vigente; AC-001, AC-005, AC-006 e AC-007 permanecem
  parciais pelas ressalvas de ambiente/execução real registradas nesta seção.
- [~] T-021: o procedimento de checkout limpo, bootstrap, smoke test e diagnóstico de
  volumes foi consolidado e aprovado; a execução independente do roteiro ainda não foi
  repetida nesta revisão.
- [x] T-022: esta matriz e a revisão cruzada Spec ↔ Plano/Tarefas ↔ Código ↔ Testes foram
  atualizadas; riscos residuais e divergências permanecem explícitos abaixo.
- [x] T-023: o procedimento de deploy/rollback foi revisado e aprovado por Marcos; nenhum
  deploy foi executado nesta rodada.

### Requisitos de prioridade alta e regressão — rodada vigente

- [x] Caminho feliz ponta a ponta: `DocumentRegistrationEndToEndTest` usou PostgreSQL,
  RabbitMQ e MinIO gerenciados pela suíte; publicação, consumo, storage final,
  `COMPLETED` e remoção do staging passaram.
- [x] Reconciliação: testes unitários e PostgreSQL real cobrem claim exclusivo, agenda
  persistida, `exists=true`, backoff aprovado e quinta tentativa em `FAILED`.
- [x] Falha permanente: testes cobrem `REJECTED`/falha definitiva identificável em
  `FAILED` e mensagem inválida em DLQ sem alterar documento.
- [~] Retries e DLQ: a lógica unitária cobre `5s/15s/60s`, DLQ e requeue; o broker real
  confirmou somente o caminho de `5s` nesta rodada.
- [~] Timeout real: configuração MinIO e tradução controlada para `RESULT_UNKNOWN`
  estão cobertas; o limite do processamento completo da entrega não está comprovado.
- [~] Concorrência: claim e storage idempotente foram exercitados com PostgreSQL e
  MinIO reais; duplicação através de mensagens/listeners RabbitMQ reais não foi
  executada.
- [x] Isolamento: `./mvnw.cmd test` passou sem dependência externa implícita de
  RabbitMQ, e os testes usam containers/configurações próprias quando necessário.
- [~] Documentação: as fontes atuais estão alinhadas no fluxo principal, mas há o link
  de Spec antigo na arquitetura e uma revisão histórica ainda listada no README.
- [x] Regressão automatizada: `./mvnw.cmd test` executado em 2026-09-19 terminou com
  92 testes, 0 falhas, 0 erros e 0 ignorados. Houve warnings de encerramento de
  containers/listeners durante teardown, sem falha de teste.

### Arquitetura, versionamento e entrega — rodada vigente

- [x] A implementação respeita os limites funcionais: scheduler em `document/application`,
  RabbitMQ na borda de mensageria, MinIO atrás de adaptadores/portas e nenhuma
  autenticação, frontend, multi-tenant ou novo fluxo de negócio.
- [~] A organização de pacotes ainda mantém classes legadas (`DocumentService`,
  `DocumentRepository` e `document.storage.DocumentStorage/MinioDocumentStorage`) fora
  da árvore-alvo completa descrita na arquitetura; a própria arquitetura registra essa
  evolução incremental, mas a convergência estrutural não é total.
- [x] Flyway aplicou V1–V5 nos testes de contexto e integração; não foi feita edição
  direta de dados ou remoção de volumes.
- [x] O Compose é válido com `infra/.env.example`; `infra/.env` existe localmente e o
  artefato de rollback existe.
- [ ] O worktree não está limpo/promovível: há alterações modificadas, arquivos não
  rastreados, exclusões legadas e `backend/dockflow.pid`. Não é possível atribuir esse
  estado a um artefato exato de deploy.
- [ ] A aprovação do deploy não está registrada. Esta revisão não aprova deploy.

### Divergências que permanecem antes do deploy

1. O timeout de 3 segundos está aplicado por chamada do cliente MinIO, não como uma
   deadline única do processamento completo da entrega RabbitMQ.
2. A integração RabbitMQ real não aguarda os TTLs de 15s e 60s nem reproduz todos os
   caminhos reais de `ack`/`nack` e falha de publicação do retry.
3. O teste de concorrência real não atravessa duas mensagens e dois listeners RabbitMQ;
   valida o núcleo provider-neutral com PostgreSQL e MinIO.
4. `specs/02-arquitetura/ARQUITETURA.md` referencia o caminho antigo
   `specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md`
   em vez da Spec consolidada `backend-operacional-docflow`.
5. O worktree contém artefatos e alterações fora de um commit/ref promovível, incluindo
   `backend/dockflow.pid`; o diff final precisa ser separado e revisado.
6. `docs/document-registration-delivery-review.md` está rotulado como histórico, mas
   continua listado no README e contém resultados anteriores que podem confundir uma
   leitura operacional rápida.

O caminho solicitado não existia literalmente no repositório; este arquivo foi criado
como o checklist consolidado da feature. O checklist específico anterior permanece em
`specs/05-verificacao/registro-binario-e-armazenamento-assincrono-documentos/checklist.md`.

Legenda: `[x]` confirmado; `[~]` confirmado parcialmente ou com ressalva; `[ ]` não
confirmado.

## Evidência histórica — revisão anterior

As seções abaixo preservam a auditoria realizada em 2026-09-17. Itens marcados como
parciais ou não confirmados refletem o estado anterior à rodada do backend operacional;
não substituem a evidência vigente acima e não devem ser usados como fonte primária.

## 1. Spec ↔ Plano/Tarefas

- [x] FR-001 a FR-017 estão referenciados no plano e nas tarefas da feature.
- [x] A ordem aprovada está refletida no plano e no código: staging → persistência
  `PENDING` → publicação confirmada.
- [x] Falha de confirmação mantém o documento `PENDING` e preserva o staging.
- [x] O contrato de sucesso preserva `201 Created`, `Location`, `DocumentResponse`,
  `status: PENDING` e `sizeBytes` real.
- [x] T-001 a T-028 estão marcadas como concluídas; T-004-E permanece corretamente
  não executada porque o gate de claim/lease passou.
- [x] A feature não criou migration própria nem alterou V1–V3.
- [~] O plano registra a organização-alvo por `domain/application/port/adapter`, mas
  parte da implementação permanece nos pacotes legados `document`,
  `document.storage` e `document.controller`. A divergência é detalhada na seção 4.
- [ ] O plano não define uma matriz explícita de prioridade alta (P0/P1) em um plano
  de verificação separado. As prioridades abaixo foram inferidas do caminho principal,
  dos ACs e dos riscos altos do próprio plano.

## 2. Requisitos funcionais e critérios de aceite

### Entrada, validação e contrato HTTP

- [x] FR-001 a FR-003: `POST /documents` multipart, contrato `DocumentContent` com
  `InputStream` e `MultipartFile` restrito ao controller. Evidências: `DocumentController`,
  `DocumentMultipartRegistrationRequest`, `DocumentContentBoundaryTest` e
  `DocumentMultipartRegistrationRequestTest`.
- [x] FR-004 a FR-007: Tika, conteúdo vazio, filename/content type, assinatura,
  limite de 52428800 bytes, `ProblemDetail` e rejeição do JSON anterior. Evidências:
  `DocumentContentValidatorTest` e 12 testes em `DocumentControllerTest`.
- [x] AC-002: arquivo ausente retorna `400` sem chamar o serviço.
- [~] AC-003: as validações são cobertas em testes de controller e validador separados;
  não há um teste real único que atravesse multipart HTTP, Tika e staging para cada
  variação inválida.

### Staging, persistência e publicação

- [x] FR-008 e FR-009: staging separado, referência `staging/{documentId}`, contagem
  efetiva e fechamento do stream. Evidências: `MinioDocumentStagingTest`,
  `DocumentBinaryRegistrationServiceTest` e smoke local.
- [x] FR-010 e FR-011: mensagem JSON v1 persistente com os seis campos, sem binário,
  routing key aprovada e publisher confirm síncrono. Evidências:
  `RabbitMqDocumentStorageRequestPublisherTest` e E2E com RabbitMQ real.
- [x] AC-001: caminho feliz ponta a ponta passou com PostgreSQL, MinIO e RabbitMQ em
  Testcontainers; o smoke local também confirmou `201`, `COMPLETED`, objeto final e
  remoção posterior do staging.
- [x] AC-004: teste do Application Service confirma `flush`, estado `PENDING` e
  ausência de rollback quando a publicação não é confirmada.
- [~] AC-004 não foi reproduzido com uma falha de confirmação em um broker local real;
  a preservação do staging nesse cenário foi confirmada por teste de porta falsa e
  pelo código, não por uma simulação de broker real.

### Consumidor, idempotência e retries

- [x] FR-012 a FR-014: leitura provider-neutral, claim antes da leitura, `exists`,
  `store` e remoção somente após confirmação. Evidências:
  `DocumentStorageMessageConsumerTest`, `MinioDocumentStagingTest` e E2E.
- [x] FR-016: logs estruturados de publicação, consumo, sucesso, falha e disputa de
  claim incluem `documentId`; há teste específico de logs.
- [x] AC-005: mensagem inválida é rejeitada diretamente para a rota de dead letter e
  não chama storage; coberto pelo handler.
- [x] AC-007: objeto final existente não chama `store`, conclui de forma idempotente e
  preserva o staging; coberto pelo consumidor.
- [~] FR-015 e AC-006: a progressão pelas filas de 5s, 15s e 60s, contagem de três
  retries, DLQ e requeue sem confirmação estão cobertos por testes unitários do
  handler, mas não foram aguardados/executados em broker real com os TTLs completos.
- [~] FR-017 e AC-008: o PostgreSQL confirmou atomicidade, disputa antes de 9999 ms e
  takeover em 10000 ms; porém não há teste com dois consumidores reais concorrendo em
  threads/processos simultâneos nem recuperação após queda real do consumidor.

## 3. Requisitos de alta prioridade e regressão

Não existe uma lista formal de prioridades no plano; foram tratados como alta
prioridade os riscos e caminhos AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007
e AC-008.

- [x] Caminho feliz completo: E2E com PostgreSQL, MinIO e RabbitMQ reais isolados.
- [x] Contrato multipart e rejeição do JSON antigo: testes MVC.
- [x] Validação de conteúdo, tamanho real e fechamento de stream: testes do validador
  e staging.
- [x] Ordem staging → persistência → publicação e estado `PENDING` em falha de
  publicação: teste do Application Service.
- [x] Idempotência, limpeza condicional e claim/lease básico: testes do consumidor e
  integração PostgreSQL.
- [~] Retries, DLQ e backoff: cobertura unitária completa do roteamento, mas sem
  confirmação dos atrasos efetivos em broker real.
- [~] Concorrência: claim condicional e lease validados no banco, concorrência real de
  consumidores não confirmada.
- [ ] Timeout máximo de processamento de uma entrega (`3000 ms`): existe a constante
  `PROCESSING_TIMEOUT`, usada para confirmação de publicação e retry, mas não foi
  identificado mecanismo que imponha limite ao processamento total do consumidor nem
  teste específico desse timeout.
- [x] Regressão do backend: `./mvnw.cmd -q clean verify` passou com 73 testes, 0 falhas,
  0 erros e 0 ignorados, incluindo testes de registro, consulta, estados, reconciliação,
  storage e contexto da aplicação.
- [~] A regressão foi executada no worktree atual, não em um commit limpo/promovido;
  portanto, não confirma que o artefato obtido por Git conterá exatamente o mesmo
  conjunto de arquivos.

## 4. Arquitetura e operação

- [x] O controller é o único componente que importa `MultipartFile`.
- [x] O contrato de conteúdo e as portas de staging são provider-neutral.
- [x] Publisher e consumidor ficam na área `document/messaging/rabbitmq/`; o adaptador
  de staging usa MinIO somente na borda.
- [x] RabbitMQ, PostgreSQL e os dois buckets MinIO estão configurados no Compose local;
  o smoke confirmou as filas aprovadas e o fluxo completo.
- [x] Não foram introduzidos outbox, transação distribuída, frontend ou nova migration
  para esta feature.
- [~] A arquitetura alvo documenta `document/domain`, `document/application`,
  `document/port/out/storage` e `document/adapter`, mas `DocumentService`,
  `DocumentRepository` e o domínio principal continuam na raiz `document`; o
  `DocumentStorage` e `MinioDocumentStorage` continuam no pacote legado
  `document.storage`. Isso não impediu o fluxo, mas é divergência estrutural que deve
  ser aceita conscientemente ou corrigida em tarefa própria.
- [ ] `infra/.env.example` não é executável sem preenchimento: as credenciais estão
  vazias e o Compose exige credenciais do RabbitMQ. O smoke local precisou de variáveis
  efêmeras fora do repositório; não foi confirmado que outro desenvolvedor consiga
  iniciar a stack seguindo apenas o exemplo sem preparação adicional.
- [~] A `SPEC_PRODUTO.md` ainda contém redação anterior que classifica RabbitMQ e
  armazenamento final como evolução futura, enquanto a Spec da feature e a arquitetura
  atual já os incluem. É divergência documental, não do fluxo executado.

## 5. Versionamento e entrega

- [x] Existe procedimento operacional com smoke test, rollback, preservação de banco,
  mensagens e staging em `specs/06-deploy/registro-binario-e-armazenamento-assincrono-documentos/deploy.md`.
- [x] O checklist específico da feature e este checklist registram os resultados dos
  testes e o smoke local.
- [ ] Não foi possível confirmar um artefato limpo e promovível: `git status` mostra
  alterações, arquivos não rastreados, exclusões de documentação legada e o arquivo
  local `backend/dockflow.pid`. A revisão deve ser repetida sobre o commit exato que
  será usado no deploy.
- [ ] A aprovação final de Marcos ainda não foi registrada. Este checklist não aprova
  o deploy.

## 6. Divergências e ações recomendadas antes do deploy

1. Decidir se a permanência dos pacotes legados (`DocumentService`, repositório e
   `DocumentStorage`) é aceita como evolução incremental da arquitetura ou abrir uma
   tarefa/decisão para a migração estrutural.
2. Definir ou testar explicitamente o timeout total de processamento de `3000 ms`; hoje
   o código só usa esse valor como timeout de confirmação RabbitMQ.
3. Executar um teste com concorrência real entre consumidores e um teste com broker real
   para publicação não confirmada, retry, TTL e DLQ.
4. Separar o diff da feature, remover artefatos locais como `backend/dockflow.pid` e
   repetir a revisão no commit que será promovido.
5. Atualizar a Spec do Produto ou registrar explicitamente que ela é histórica, para
   não manter a feature atual descrita como backlog.

## Resultado

A implementação converge funcionalmente no caminho feliz, no contrato HTTP, no staging,
na publicação confirmada, na idempotência básica e na regressão executada. A
convergência não é total: timeout de processamento, concorrência real, falhas/retries
com broker real, estrutura final de pacotes, reprodutibilidade do Compose a partir do
`.env.example` e o artefato limpo/promovível permanecem não confirmados ou divergentes.

Deploy: **não aprovado por esta revisão**; a decisão permanece com Marcos.
