# Tarefas — Completar e estabilizar o backend operacional do DocFlow

Plano relacionado: `specs/04-plano/backend-operacional-docflow/plano.md`

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente para revisão isolada, referenciar o requisito
`FR-XXX` que implementa e declarar como verificar que ficou pronta. A ordem abaixo é
obrigatória. Nenhuma tarefa de lógica deve começar enquanto os gates da Fase 1 não
estiverem resolvidos.

## Fase 1 — Base

- [x] T-001 [FR-001][FR-011] Mapear a Spec aprovada contra o código, testes, infraestrutura e documentação existentes, registrando o que está apenas no worktree e o que tem evidência executável.
  - Arquivos/módulos: `specs/03-features/backend-operacional-docflow/spec.md`, `specs/02-arquitetura/`, `backend/src/`, `infra/`, `docs/`, `README.md`.
  - Verificação: matriz FR/AC → arquivo/teste/documento preenchida; nenhuma alteração local é marcada como concluída sem teste; o caminho oficial `backend-operacional-docflow` é confirmado.

- [x] T-002 [FR-003][FR-004][FR-005] Definir o modelo persistido da reconciliação: próxima data elegível, contador de tentativas, estado mínimo de claim e tratamento de documentos já `PROCESSING`.
  - Arquivos/módulos: `Document.java`, `DocumentRepository.java`, `DocumentReconciliationService.java`, `backend/src/main/resources/db/migration/`.
  - Verificação: revisão técnica demonstra como o scheduler retoma após reinício, como apenas um executor obtém o claim e como a quinta tentativa leva a `FAILED`.

- [x] T-003 [FR-003][FR-004][FR-005] Implementar a migration expand-only necessária após V4, caso os campos de próxima tentativa/claim não possam ser representados pelo schema existente.
  - Arquivos/módulos: nova migration em `backend/src/main/resources/db/migration/`, `docs/database.md` e testes de migration.
  - Verificação: Flyway aplica a migration sobre banco com V1–V4, dados existentes continuam legíveis, documentos `PROCESSING` recebem valores seguros e o rollback operacional está documentado; se V4 for suficiente, registrar essa evidência e não criar migration redundante.

- [x] T-004 [FR-001][FR-008][FR-010] Separar os fixtures de teste por necessidade: unitário sem infraestrutura, integração com serviços gerenciados e end-to-end com PostgreSQL, RabbitMQ e MinIO reais.
  - Arquivos/módulos: `IntegrationTestBase.java`, `DocumentRegistrationEndToEndTest.java`, configurações de teste e novos fixtures em `backend/src/test/java/`.
  - Verificação: testes de contexto não tentam `localhost:5672` quando RabbitMQ não foi declarado; testes que exigem broker/storage iniciam suas próprias dependências e executam de forma repetível.

- [x] T-005 [FR-002][FR-006][FR-009][FR-011] Declarar o contrato de configuração operacional, incluindo variáveis obrigatórias, timeout MinIO de 3 segundos, scheduler, RabbitMQ com consumidor único/prefetch 1/concorrência 1..1 e buckets separados.
  - Arquivos/módulos: `backend/src/main/resources/application.properties`, `infra/.env.example`, `docs/document-registration-operations.md`.
  - Verificação: não há credencial obrigatória vazia com fallback silencioso; cada propriedade possui nome, finalidade e origem documentados; nenhuma credencial aparece hardcoded.

## Fase 2 — Lógica principal

- [x] T-006 [FR-003][FR-004][FR-005] Implementar no domínio e no repositório o claim atômico de reconciliação, o incremento de tentativa, a próxima elegibilidade e as transições protegidas para `COMPLETED` e `FAILED`.
  - Arquivos/módulos: `Document.java`, `DocumentRepository.java`, `DocumentReconciliationService.java`, `ReconcileUnknownStorageResultUseCase.java`.
  - Verificação: testes demonstram que dois executores não reconciliam o mesmo documento, `exists=true` termina em `COMPLETED`, e a quinta tentativa sem confirmação termina em `FAILED`.

- [x] T-007 [FR-003][FR-004][FR-005] Criar o scheduler interno periódico que consulta documentos `PROCESSING` elegíveis, obtém claim atômico, executa `exists(objectKey)` e agenda os atrasos de `1m`, `5m`, `15m`, `30m` e `60m`.
  - Arquivos/módulos: novo componente em `document/application/` ou subdivisão justificada dentro de `document/`, configuração Spring e repositório.
  - Verificação: teste controla o relógio/agendamento, comprova as cinco datas, liberação do claim em sucesso/erro e retomada após reinício; nenhum endpoint REST é necessário para o caminho principal.

- [x] T-008 [FR-005][FR-007] Ajustar o consumidor e o handler RabbitMQ para marcar `FAILED` em `REJECTED` e falhas permanentes identificáveis, mantendo `RESULT_UNKNOWN` em `PROCESSING` para retry e reconciliação.
  - Arquivos/módulos: `DocumentStorageMessageConsumer.java`, `RabbitMqDocumentStorageMessageHandler.java`, `DocumentService.java` e métodos de domínio.
  - Verificação: teste comprova `REJECTED → FAILED` antes do tratamento definitivo; mensagem inválida sem correlação não altera documento; `RESULT_UNKNOWN` não gera `COMPLETED` nem `FAILED` prematuro.

- [x] T-009 [FR-006] Configurar timeouts de conexão, leitura e escrita de 3 segundos no cliente MinIO e traduzir timeout para `RESULT_UNKNOWN`, sem cancelar thread à força.
  - Arquivos/módulos: `MinioDocumentConfiguration.java`, `MinioDocumentStorage.java`, `MinioDocumentStaging.java`, exceções de storage e propriedades.
  - Verificação: teste com operação MinIO bloqueada ou cliente controlado comprova o resultado `RESULT_UNKNOWN`, ausência de `ACK`, preservação de `PROCESSING` e continuidade para retry/reconciliação.

- [x] T-010 [FR-007][FR-008][FR-009] Declarar explicitamente no listener RabbitMQ um consumidor, `prefetch=1`, concorrência mínima `1` e máxima `1`, mantendo publisher confirm, retries de `5s/15s/60s`, DLQ e requeue definidos.
  - Arquivos/módulos: `DocumentStorageRabbitConfiguration.java`, `RabbitMqDocumentStorageListener.java`, `DocumentStorageRabbitTopology.java` e `RabbitMqDocumentStorageMessageHandler.java`.
  - Verificação: inspeção da configuração e teste de contexto confirmam os valores sem depender dos defaults; teste de broker confirma que retry publicado é confirmado antes do `ACK` original.

- [x] T-011 [FR-002] Criar o serviço separado de bootstrap no Docker Compose para aguardar PostgreSQL, RabbitMQ e MinIO, validar conexões/credenciais e criar `docflow-staging` e `docflow-documents` de modo idempotente.
  - Arquivos/módulos: `infra/docker-compose.yml`, novo serviço/script de bootstrap em `infra/` e `infra/.env.example`.
  - Verificação: em checkout limpo o bootstrap espera dependências, pode ser executado mais de uma vez sem erro, cria os dois buckets e falha explicitamente quando uma credencial ou serviço está incorreto.

## Fase 3 — Interface

- [x] T-012 [FR-003][FR-004][FR-005][FR-011] Integrar o scheduler ao contexto da aplicação, com configuração explícita de habilitação e sem criar endpoint público como mecanismo principal.
  - Arquivos/módulos: configuração Spring do domínio `document`, `DocumentReconciliationService.java` e componente do scheduler.
  - Verificação: a aplicação inicia com o scheduler configurado, executa documentos elegíveis e não expõe dependência do scheduler no controller REST.

- [x] T-013 [FR-002][FR-006][FR-009] Integrar propriedades da aplicação, credenciais do Compose, buckets e configurações de RabbitMQ/MinIO sem confundir credenciais root do servidor MinIO com credenciais da aplicação.
  - Arquivos/módulos: `application.properties`, `infra/.env.example`, `infra/docker-compose.yml`, `MinioDocumentConfiguration.java`, `DocumentStorageRabbitConfiguration.java`.
  - Verificação: o ambiente local sobe com valores documentados, falha cedo quando obrigatório está ausente e o backend aponta para os buckets corretos.

- [x] T-014 [FR-005][FR-011] Atualizar os contratos documentais e operacionais sem alterar endpoints ou adicionar novo fluxo de negócio: API, operação local, banco, README, arquitetura/ADRs e deploy/rollback.
  - Arquivos/módulos: `docs/api.md`, `docs/document-registration-operations.md`, `docs/database.md`, `README.md`, `specs/01-produto/`, `specs/02-arquitetura/`, `specs/03-features/` relacionadas, `specs/06-deploy/`.
  - Verificação: uma leitura cruzada encontra uma única descrição para `RESULT_UNKNOWN`, retries, DLQ, estados, timeout, scheduler e bootstrap; `specs/05-verificacao/` é identificado como evidência, não como fonte primária.

## Fase 4 — Testes

- [x] T-015 [FR-003][FR-004][FR-005][FR-006] Cobrir unitariamente reconciliação, backoff, quinta tentativa, transições permanentes e timeout MinIO.
  - Arquivos/módulos: testes de `document/application/`, domínio e storage MinIO.
  - Verificação: casos cobrem `exists=true`, `exists=false` com tentativas restantes, quinta falha, `REJECTED`, timeout como `RESULT_UNKNOWN` e ausência de `ACK`/sucesso indevido.

- [x] T-016 [FR-001][FR-003][FR-004][FR-005] Validar com PostgreSQL real a migration, claim atômico, próxima elegibilidade, lease de processamento e concorrência de dois schedulers.
  - Arquivos/módulos: `IntegrationTestBase.java`, testes de repositório/serviço e migrations.
  - Verificação: dois executores concorrentes resultam em um único claim; reinício preserva o contador/data; takeover de processamento continua respeitando a lease de 10 segundos.

- [x] T-017 [FR-007][FR-008][FR-009] Validar com RabbitMQ real a topologia, publisher confirm, `ack`, `nack`, requeue, retries de `5s/15s/60s`, DLQ direta e mensagem inválida.
  - Arquivos/módulos: novos testes de integração RabbitMQ em `backend/src/test/java/.../messaging/rabbitmq/`, fixture Testcontainers e configuração da aplicação.
  - Verificação: o teste observa a mensagem nas filas corretas, confirma quatro entregas no máximo para falha transitória, DLQ direta para inválida/permanente e requeue quando a publicação do retry falha.

- [x] T-018 [FR-002][FR-006][FR-008] Validar com MinIO real o bootstrap, buckets, credenciais, gravação/leitura e falhas de timeout, mantendo mocks nos testes unitários.
  - Arquivos/módulos: `DocumentRegistrationEndToEndTest.java`, testes de staging/storage e fixture MinIO.
  - Verificação: os dois buckets são criados sem intervenção manual; credencial incorreta falha explicitamente; operação com timeout produz `RESULT_UNKNOWN` e preserva staging/estado conforme a Spec.

- [x] T-019 [FR-009][FR-010] Executar teste de concorrência real com mensagens duplicadas e consumidores configurados, validando claim exclusivo e ausência de gravação duplicada.
  - Arquivos/módulos: testes RabbitMQ/PostgreSQL/MinIO do domínio `document`.
  - Verificação: somente um processamento chama `store`, o consumidor perdedor não remove staging e o estado final é consistente; a configuração observada é consumidor 1, `prefetch=1`, concorrência `1..1`.

- [x] T-020 [FR-001][FR-007][FR-008][FR-011] Executar a regressão completa do backend e eliminar as sete falhas de contexto sem reintroduzir conexão externa implícita.
  - Arquivos/módulos: toda a suíte em `backend/src/test/java/`, build Maven e `specs/05-verificacao/`.
  - Verificação: unitários, contextos, integração e end-to-end passam em ambiente controlado; a execução sem RabbitMQ externo não tenta `localhost:5672`; divergências funcionais e ambientais são separadas e registradas.

## Fase 5 — Entrega

- [x] T-021 [FR-002][FR-011] Preparar o procedimento de execução local a partir de checkout limpo, incluindo `.env.example`, bootstrap, smoke test, diagnóstico de volumes persistentes e preservação de dados.
  - Arquivos/módulos: `README.md`, `docs/document-registration-operations.md`, `infra/` e `specs/06-deploy/`.
  - Verificação: outra pessoa consegue subir o ambiente, criar buckets, registrar/processar um documento e diagnosticar credencial incorreta sem instruções fora dos documentos oficiais.

- [x] T-022 [FR-001][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011] Executar a revisão de convergência `Spec ↔ Plano/Tarefas ↔ Código ↔ Teste` e atualizar as evidências de validação.
  - Arquivos/módulos: Spec, plano, tarefas, código, testes, `specs/05-verificacao/` e checklist de convergência.
  - Verificação: cada FR e AC possui implementação e teste identificáveis; riscos residuais, migration, configuração, rollback e divergências estão registrados antes da entrega.

- [x] T-023 [FR-002][FR-011] Revisar deploy/rollback e obter aprovação de Marcos antes de qualquer deploy.
  - Arquivos/módulos: `specs/06-deploy/`, `docs/`, migrations e diff final.
  - Verificação: procedimento preserva PostgreSQL, objetos de staging e mensagens RabbitMQ, não remove volumes automaticamente, define retorno seguro e registra aprovação explícita; nenhum deploy é executado nesta rodada.

## Adiado (fora do escopo desta rodada)

- [ ] Criar frontend Angular, autenticação, autorização ou multi-tenant.
- [ ] Criar novos fluxos de negócio ou novos endpoints públicos para substituir o
  scheduler.
- [ ] Implementar outbox, transação distribuída ou compensação automática entre
  PostgreSQL, RabbitMQ e MinIO.
- [ ] Implementar limpeza automática de objetos órfãos de staging ou storage final após
  `FAILED`.
- [ ] Introduzir tracing distribuído, métricas avançadas ou outra infraestrutura global
  não exigida pelos FRs desta feature.
