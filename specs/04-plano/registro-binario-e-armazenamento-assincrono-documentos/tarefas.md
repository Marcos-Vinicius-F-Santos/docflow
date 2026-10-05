# Tarefas — Registro binário e armazenamento assíncrono de documentos

Plano relacionado: specs/04-plano/registro-binario-e-armazenamento-assincrono-documentos/plano.md

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente para revisar de uma vez, referenciar o
requisito `FR-XXX` que implementa e dizer como verificar que ficou pronta. A ordem abaixo
é obrigatória; nenhuma tarefa de lógica deve começar enquanto os gates da Fase 1 não
estiverem resolvidos.

## Fase 1 — Base

- [x] T-001 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Consolidar os contratos da Spec aprovada, da ADR-004 e da arquitetura alvo, identificando os planos históricos como referência não executável.
  - Arquivos/módulos: `specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md`, `specs/02-arquitetura/ARQUITETURA.md`, `specs/02-arquitetura/DECISAO/ADR-001*`, `ADR-002*`, `ADR-003*`, `ADR-004*`.
  - Verificação: revisão documental confirma que API, staging, mensagem, consumidor, retry, lease, ausência de migration e critérios AC-001 a AC-008 estão alinhados.

- [x] T-002 [FR-002][FR-003][FR-009][FR-012][FR-014] Definir as portas provider-neutral de conteúdo e staging, incluindo ownership do `InputStream`, abertura por `contentReference`, escrita no bucket separado e remoção condicionada.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/port/out/storage/`, `document/application/`.
  - Verificação: contratos não importam Spring Web, MinIO ou RabbitMQ; os testes de compilação mostram claramente quem fecha o stream e quais operações podem remover staging.

- [x] T-003 [FR-010][FR-011][FR-015][FR-016] Definir o contrato JSON v1 e a topologia RabbitMQ da feature, incluindo exchange, fila principal, routing key, DLX, DLQ, filas de retry, persistência, timeout de `3000 ms` e confirmação síncrona.
  - Arquivos/módulos: `document/messaging/rabbitmq/`, documentação da feature e configuração de mensageria.
  - Verificação: um contrato versionado enumera somente os seis campos obrigatórios; nomes e atrasos `5s/15s/60s` coincidem com a Spec.

- [x] T-004 [FR-017] Confirmar o mecanismo de claim atômico, controle de versão e lease de `10000 ms` usando apenas o schema existente.
  - Arquivos/módulos: `Document.java`, `DocumentRepository.java` ou adaptador equivalente em `document/adapter/out/persistence/`, migrations V1–V3.
  - Verificação: teste ou análise de persistência demonstra que apenas um consumidor obtém `PENDING → PROCESSING`, takeover só ocorre após expiração e nenhuma migration nova é necessária; se falhar, a tarefa fica bloqueada e a divergência é registrada.

- [ ] T-004-E [FR-017] **Tarefa condicional de emergência — executar somente se T-004 falhar:** criar o suporte mínimo para claim/lease sem alterar V1–V3, com migration Flyway expand-only e atualização prévia da Spec/decisão necessária.
  - Arquivos/módulos: nova migration em `backend/src/main/resources/db/migration/`, `Document.java`, persistência em `document/adapter/out/persistence/` e testes de concorrência.
  - Verificação: a Spec e o Registro de Decisão aplicável estão aprovados antes do código; a migration é aditiva, o claim é atômico, a lease de `10000 ms` só permite takeover após expiração e o rollback preserva o schema expandido.

- [x] T-005 [FR-004][FR-005][FR-006][FR-007] Fixar códigos de erro, limite de `52428800` bytes e o contrato `ProblemDetail` para validações HTTP.
  - Arquivos/módulos: `document/adapter/in/web/exception/`, `GlobalExceptionHandler.java` e documentação da API.
  - Verificação: tabela de erros cobre arquivo ausente, filename, content type, conteúdo vazio, assinatura divergente/não identificada, excesso de tamanho e JSON antigo, com `errorCode` e `invalidField` quando aplicável.

- [x] T-006 [FR-008][FR-009][FR-010][FR-011][FR-015] Fixar dependências e configuração externa sem segredos hardcoded.
  - Arquivos/módulos: `backend/pom.xml`, `application.properties`, `infra/.env.example`, `infra/docker-compose.yml`.
  - Verificação: dependências resolvem; propriedades distinguem bucket final e `MINIO_STAGING_BUCKET`; RabbitMQ local está definido ou o bloqueio de ambiente fica registrado; nenhum segredo aparece no diff.

## Fase 2 — Lógica principal

- [x] T-007 [FR-002][FR-003] Criar o contrato `DocumentContent` e o mapeamento interno da parte `file`, contendo apenas `InputStream`, filename e content type.
  - Arquivos/módulos: `document/application/`, `document/adapter/in/web/dto/`.
  - Verificação: teste de mapeamento passa e busca de imports confirma que `MultipartFile` não atravessa o adaptador de entrada.

- [x] T-008 [FR-004][FR-005] Implementar a validação do conteúdo com Apache Tika, conteúdo não vazio, comparação com o content type declarado e limite máximo.
  - Arquivos/módulos: `document/application/`, exceções de conteúdo e dependências Tika/Commons IO.
  - Verificação: testes cobrem assinatura compatível, divergente, não identificada, conteúdo vazio, content type ausente e tamanhos no limite e acima dele; todos resultam em erro interno estável.

- [x] T-009 [FR-003][FR-005][FR-009] Implementar a leitura efetiva com `CountingInputStream`, preservando streaming, contando o tamanho real e fechando o stream em sucesso e falha.
  - Arquivos/módulos: validador/serviço de conteúdo em `document/application/` e porta de staging.
  - Verificação: teste com stream sem `mark/reset` comprova que os bytes gravados e `sizeBytes` coincidem, sem `byte[]` ou arquivo temporário como contrato.

- [x] T-010 [FR-008][FR-009][FR-012][FR-014] Implementar o adaptador MinIO de staging para escrever, abrir e remover `staging/{documentId}` no bucket `docflow-staging`.
  - Arquivos/módulos: `document/adapter/out/storage/minio/`, configuração MinIO e porta `DocumentStaging`.
  - Verificação: teste do adaptador comprova bucket separado, referência correta, leitura por referência, fechamento do stream e tradução de falhas do SDK.

- [x] T-011 [FR-001][FR-005][FR-008][FR-009][FR-010][FR-011] Atualizar o Application Service para gerar o identificador, gravar primeiro o staging, persistir o registro como `PENDING` e publicar somente após a persistência.
  - Arquivos/módulos: `document/application/DocumentService.java`, `Document.java`, portas de staging e publicação.
  - Verificação: teste unitário verifica a ordem staging → persistência `PENDING` → publicação, `contentReference`, tamanho contado e ausência de persistência/publicação quando a validação ou o staging falham.

- [x] T-012 [FR-010][FR-011][FR-016] Implementar o publisher RabbitMQ da mensagem `DocumentStorageRequested` v1 com routing key aprovada e publisher confirm síncrono.
  - Arquivos/módulos: `document/application/DocumentStorageRequestPublisher.java`, `document/messaging/rabbitmq/`.
  - Verificação: teste de contrato confirma os seis campos, nenhum binário/stream, mensagem persistente e que somente confirmação clara gera resultado de publicação bem-sucedida.

- [x] T-013 [FR-012][FR-013][FR-014][FR-017] Implementar o núcleo do consumidor, com validação de schema, claim/lease, leitura do staging, `exists(objectKey)`, `store` idempotente e limpeza somente após confirmação.
  - Arquivos/módulos: `document/messaging/rabbitmq/`, portas de staging/storage e persistência de documentos.
  - Verificação: testes comprovam que mensagens válidas usam a porta provider-neutral, objetos existentes não chamam `store`, falhas de `store` não removem staging e duplicatas não gravam novamente.

- [x] T-014 [FR-015] Implementar acknowledgment, DLQ direta para mensagem inválida e retry de falhas transitórias nas filas de `5s`, `15s` e `60s`, totalizando três retries além da entrega inicial.
  - Arquivos/módulos: configuração do consumidor e `document/messaging/rabbitmq/`.
  - Verificação: testes contam quatro entregas possíveis, validam os destinos/atrasos, confirmam ausência de retry para schema inválido e ausência de acknowledgment prematuro.

- [x] T-015 [FR-016][FR-017] Adicionar logs estruturados de publicação, início do consumo, sucesso, falha e disputa de claim, sempre com `documentId`.
  - Arquivos/módulos: publisher, consumidor e configuração de logging.
  - Verificação: teste ou inspeção controlada encontra os quatro eventos e a correlação; logs não contêm binário, token ou credencial.

## Fase 3 — Interface

- [x] T-016 [FR-001][FR-002][FR-006][FR-007] Alterar `DocumentController` para aceitar somente multipart com a parte obrigatória `file` e traduzir a entrada para `DocumentContent`.
  - Arquivos/módulos: `document/adapter/in/web/DocumentController.java`, DTOs e mappers da entrada.
  - Verificação: teste MVC aceita multipart válido, rejeita ausência de `file`, não aceita metadata duplicada e rejeita JSON somente com metadados; a resposta de sucesso aprovada é `201 Created` com `Location` e `DocumentResponse` contendo `status: PENDING` e tamanho real.

- [x] T-017 [FR-004][FR-005][FR-006] Integrar o mapeamento de erros de conteúdo ao `ProblemDetail` com `application/problem+json`.
  - Arquivos/módulos: `document/adapter/in/web/exception/GlobalExceptionHandler.java` e exceções da feature.
  - Verificação: cada erro de validação responde HTTP `400`, content type correto, `errorCode` estável e `invalidField` quando aplicável; não persiste, publica ou grava staging.

- [x] T-018 [FR-008][FR-009][FR-010][FR-011][FR-015] Registrar o wiring de MinIO, staging, publisher, consumidor e topologia RabbitMQ no contexto da aplicação e no Docker Compose local.
  - Arquivos/módulos: configurações Spring em `document/`, `application.properties`, `infra/docker-compose.yml` e `.env.example`.
  - Verificação: o Compose inicia PostgreSQL, MinIO e RabbitMQ; o contexto conecta aos três serviços, o bucket de staging é distinto do final e a aplicação não usa credenciais hardcoded.

- [x] T-019 [FR-001][FR-006][FR-007][FR-016] Atualizar a documentação da API e de operação, incluindo breaking change, smoke test, correlação por `documentId` e preservação de staging em falhas.
  - Arquivos/módulos: `docs/api.md`, `docs/`, `specs/05-verificacao/`.
  - Verificação: outra pessoa consegue identificar o request multipart, os erros, a resposta esperada e o procedimento sem depender de detalhes implícitos do código.

## Fase 4 — Testes

- [x] T-020 [FR-001][FR-004][FR-005][FR-006][FR-007] Cobrir o contrato HTTP do controller.
  - Arquivos/módulos: `backend/src/test/java/com/dockflow/dockflow/document/adapter/in/web/` e testes de controller existentes.
  - Verificação: caminho feliz, `file` ausente, filename/content type inválidos, conteúdo vazio, assinatura incompatível, tamanho acima do limite e JSON antigo têm resultados esperados.

- [x] T-021 [FR-002][FR-003][FR-004][FR-005][FR-009] Cobrir o contrato interno de conteúdo, Tika, `CountingInputStream` e fechamento do stream.
  - Arquivos/módulos: testes de `document/application/` e validação de conteúdo.
  - Verificação: streams não reposicionáveis são aceitos quando válidos, o tamanho real é persistido/publicado e nenhuma implementação usa bufferização integral.

- [x] T-022 [FR-008][FR-009][FR-010][FR-011] Cobrir Application Service, staging e publisher com portas falsas.
  - Arquivos/módulos: testes de aplicação, staging e publisher.
  - Verificação: a ordem staging → persistência `PENDING` → publicação → resposta é comprovada; falha de staging não persiste nem publica; falta de confirmação mantém o documento `PENDING`, não retorna aceitação e preserva staging.

- [x] T-023 [FR-012][FR-013][FR-014][FR-017] Cobrir o consumidor, idempotência, limpeza e concorrência.
  - Arquivos/módulos: testes do consumidor, `DocumentStorage`, `DocumentStaging` e persistência.
  - Verificação: `exists=true` não chama `store` e preserva staging; `exists=false` segue leitura → store → delete → ack; somente um claim vence e takeover respeita a lease.

- [x] T-024 [FR-015][FR-016] Cobrir retries, DLQ, acknowledgment e logs estruturados.
  - Arquivos/módulos: testes RabbitMQ do consumidor e testes de observabilidade.
  - Verificação: mensagens inválidas vão direto à DLQ; falhas transitórias têm exatamente três retries nos atrasos aprovados; eventos contêm `documentId`.

- [x] T-025 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Executar o caminho ponta a ponta com PostgreSQL, MinIO e RabbitMQ isolados e a regressão mínima do backend.
  - Arquivos/módulos: Testcontainers/configuração de integração e `backend/src/test/java/`.
  - Verificação: AC-001 a AC-008 passam com RabbitMQ real; staging ocorre antes da persistência, publicação confirmada sucede o registro `PENDING`, `GET /documents/{id}`, estados e testes existentes continuam passando; qualquer falha ambiental é registrada separadamente de falha funcional.

## Fase 5 — Entrega

- [x] T-026 [FR-008][FR-009][FR-010][FR-011][FR-015][FR-016] Preparar o procedimento de operação, smoke test e rollback para staging, RabbitMQ, DLQ, retries e logs.
  - Arquivos/módulos: `specs/06-deploy/registro-binario-e-armazenamento-assincrono-documentos/`, `docs/` e `infra/`.
  - Verificação: o procedimento orienta preservar banco, objetos e mensagens, manter o documento `PENDING` quando publisher confirm falhar, não apagar volumes e correlacionar falhas pelo `documentId`.

- [x] T-027 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Revisar o diff final contra Spec, plano, tarefas, arquitetura e testes.
  - Arquivos/módulos: todos os arquivos da feature e checklist de `specs/05-verificacao/`.
  - Verificação: cada FR e AC aponta para implementação e teste; migrations não foram alteradas; riscos e divergências residuais estão registrados.

- [x] T-028 [FR-001][FR-008][FR-010][FR-011][FR-015][FR-017] Executar build, testes, smoke test local e revisão final antes do deploy.
  - Arquivos/módulos: backend, infraestrutura local e procedimento de deploy.
  - Verificação: build e suíte indicada passam; o smoke test comprova staging, confirmação, consumo, storage final, limpeza condicional e consulta; Marcos aprova antes de promover.

## Adiado (fora do escopo desta rodada)

- [ ] Implementar frontend Angular ou novos endpoints de acompanhamento.
- [ ] Implementar outbox, transação distribuída ou compensação automática entre PostgreSQL, MinIO e RabbitMQ.
- [ ] Definir ou implementar a política de reconciliação de `RESULT_UNKNOWN`.
- [ ] Criar migration ou alterar V1–V3 para suportar esta feature sem aprovação explícita da divergência.
- [ ] Adicionar antivírus, autenticação, autorização, lifecycle automático do bucket ou tracing distribuído.
- [ ] Introduzir compatibilidade simultânea com o POST JSON sem uma nova decisão de contrato.
