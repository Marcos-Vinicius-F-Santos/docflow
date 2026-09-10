# Tasks — Armazenamento binário de documentos

## Task Rules

Cada task deve ser:

- independentemente compreensível;
- pequena o suficiente para revisão;
- vinculada aos requisitos quando aplicável;
- explícita sobre sua verificação;
- executada respeitando a ordem e os gates do plano.

Referências utilizadas:

- Feature Spec: `specs/02-product/document-upload/spec.md`
- Architecture: `specs/03-architecture/document-upload/architecture.md`
- ADR: `specs/01-decisions/ADR/ADR-001-document-binary-storage.md`
- Plano: `specs/06-plans/document-upload/plan.md`

## Phase 1 — Foundation

- [x] T-001 [FR-001, FR-006] Fechar o contrato lógico da operação de registro com conteúdo binário
  - Files / modules: `specs/04-contracts/document-upload/openapi.yaml`
  - Verification: OpenAPI define entrada com metadados e conteúdo, resultado de sucesso/falha/resultado inconclusivo e erros provider-neutral; contrato revisado antes de alterar controller ou DTO.

- [x] T-002 [FR-004, FR-008, NFR-002, NFR-008] Documentar o modelo de dados e a compatibilidade com documentos legados
  - Files / modules: `specs/04-contracts/document-upload/data-model.md`
  - Verification: modelo descreve referência do objeto, estados, nulabilidade para registros legados, unicidade, constraints e forward recovery.

- [x] T-003 [NFR-004, NFR-005] Criar requisitos de segurança e threat model da operação de storage
  - Files / modules: `specs/05-security/document-upload/security-requirements.md`, `specs/05-security/document-upload/threat-model.md`
  - Verification: credenciais, bucket privado, limites de upload, entrada não confiável, logs sem conteúdo e autenticação/autorização possuem ameaças, controles e evidências definidas.

- [x] T-004 [AC-008, NFR-006, NFR-007] Criar plano de verificação, observabilidade e runbook da feature
  - Files / modules: `specs/07-verification/document-upload/test-plan.md`, `specs/09-operations/document-upload/observability.md`, `specs/09-operations/document-upload/runbook.md`
  - Verification: cada `FR`/`NFR` aplicável possui teste ou evidência; métricas, alertas, health/readiness, reconciliação e falhas parciais estão descritos.

## Phase 2 — Domain

- [x] T-005 [FR-004, FR-008, NFR-008] Adicionar ao domínio a referência persistente do conteúdo sem expor setters artificiais
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/Document.java`, `backend/src/main/java/com/dockflow/dockflow/document/DocumentResponse.java`, `backend/src/main/java/com/dockflow/dockflow/document/DocumentMapper.java`
  - Verification: a referência é protegida pelo domínio, registros legados continuam representáveis e a resposta não expõe detalhes provider-specific sem alteração contratual aprovada.

- [x] T-006 [FR-006, FR-009, NFR-002] Encapsular transições de estado e controle de concorrência por documento
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/Document.java`, `backend/src/main/java/com/dockflow/dockflow/document/DocumentRepository.java`
  - Verification: somente uma operação válida pode entrar em `PROCESSING`; `COMPLETED` exige referência; tentativas concorrentes e documentos já concluídos têm comportamento determinístico.

- [x] T-007 [FR-004, FR-008, NFR-007, NFR-008] Criar migration expand-only para referência, versão e constraints
  - Files / modules: `backend/src/main/resources/db/migration/V3__add_document_storage_metadata.sql`
  - Verification: migration executa em banco vazio e com registros existentes; referências nulas legadas são aceitas; referência duplicada é rejeitada; `COMPLETED` sem referência é rejeitado; rollback/forward recovery está documentado.

- [x] T-008 [FR-002, NFR-008] Preservar as invariantes atuais durante a evolução do domínio
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/Document.java`, `backend/src/main/java/com/dockflow/dockflow/document/dto/DocumentRegistrationRequest.java`
  - Verification: nome e tipo vazios, tamanho negativo e demais entradas inválidas continuam rejeitados sem duplicar regras de forma inconsistente.

## Phase 3 — Interfaces

- [ ] T-009 [FR-003, FR-007, NFR-003] Finalizar o contrato provider-neutral de `DocumentStorage`
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java`, `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorageException.java`
  - Verification: o contrato expressa confirmação, falha conhecida e capacidades necessárias a retry/compensação/reconciliação sem importar tipos do SDK MinIO.

- [ ] T-010 [FR-003, FR-005, NFR-003] Implementar o adapter MinIO atrás do boundary de storage
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorage.java`, `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageProperties.java`, `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageConfiguration.java`
  - Verification: bytes, tamanho e tipo são enviados corretamente; erros do provider são traduzidos; nenhum SDK MinIO aparece no domínio, service, controller ou DTO.

- [ ] T-011 [FR-003, NFR-004, NFR-006] Adicionar dependências e configuração externalizada do MinIO
  - Files / modules: `backend/pom.xml`, `backend/src/main/resources/application.properties`
  - Verification: aplicação carrega endpoint, bucket e credenciais pelo ambiente; nenhum secret é incluído no código; contexto Spring cria o adapter com configuração válida.

- [ ] T-012 [FR-003, NFR-004, NFR-006] Corrigir infraestrutura local e provisionar o bucket de documentos
  - Files / modules: `infra/docker-compose.yml`, `infra/.env.example`, `infra/minio/` quando o mecanismo de provisionamento exigir artefato adicional
  - Verification: `docker compose config` é válido; PostgreSQL e MinIO iniciam; volumes estão corretamente declarados; bucket existe; credenciais são fornecidas externamente.

- [ ] T-013 [FR-003, FR-004, FR-006, FR-007] Orquestrar no service o fluxo de persistência, storage e finalização
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java`
  - Verification: validação ocorre antes do storage; estados seguem `PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`; sucesso só ocorre após confirmação do MinIO e persistência da referência; resultado inconclusivo não vira sucesso.

- [ ] T-014 [FR-001, FR-002, FR-005, FR-007] Atualizar a API de registro para o contrato aprovado
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java`, `backend/src/main/java/com/dockflow/dockflow/document/dto/DocumentRegistrationRequest.java`
  - Verification: request válido transporta conteúdo e metadados; validações rejeitam entradas inválidas; consulta existente permanece compatível para documentos legados.

- [ ] T-015 [FR-007, NFR-003, NFR-005] Mapear erros da feature para respostas estáveis e seguras
  - Files / modules: `backend/src/main/java/com/dockflow/dockflow/document/exception/GlobalExceptionHandler.java`, `backend/src/main/java/com/dockflow/dockflow/document/exception/`
  - Verification: entrada inválida, storage indisponível, falha provider, resultado inconclusivo, falha de finalização e conflito concorrente são distinguíveis sem vazar detalhes internos ou credenciais.

## Phase 4 — Tests

- [ ] T-016 [FR-002, NFR-008] Preservar e ampliar testes das invariantes do domínio
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/DocumentServiceTest.java`, testes de domínio a criar se necessário
  - Verification: testes cobrem metadados válidos, `PENDING`, nome/tipo inválidos, tamanho negativo e ausência de chamada ao repository em entradas inválidas.

- [ ] T-017 [FR-003, FR-006, FR-007] Testar o service com storage mockado
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/DocumentServiceTest.java`
  - Verification: cenários de sucesso, falha conhecida e resultado inconclusivo validam chamadas, estados, referência e ausência de sucesso prematuro.

- [ ] T-018 [FR-009, NFR-002] Testar concorrência, retry e documento já concluído
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/DocumentServiceTest.java`, testes de integração de concorrência a criar se necessário
  - Verification: duas tentativas para o mesmo documento não criam associações ambíguas; retries reutilizam a referência; documentos diferentes continuam processáveis em paralelo.

- [ ] T-019 [FR-003, FR-005, NFR-001, NFR-003] Testar o adapter contra MinIO real
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorageIntegrationTest.java`, `backend/src/test/java/com/dockflow/dockflow/IntegrationTestBase.java`
  - Verification: conteúdo persistido no MinIO é idêntico ao conteúdo enviado; tamanho e tipo são preservados; indisponibilidade e rejeições são mapeadas corretamente.

- [ ] T-020 [FR-004, FR-008, NFR-007, NFR-008] Testar integração PostgreSQL + MinIO e compatibilidade de migration
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/DocumentServiceIntegrationTest.java`, `backend/src/test/java/com/dockflow/dockflow/IntegrationTestBase.java`, migration `V3`
  - Verification: referência e estado são persistidos após confirmação; registros legados continuam consultáveis; falha entre storage e banco permanece não-successo e é reconcilável.

- [ ] T-021 [FR-001, FR-002, FR-006, FR-007] Atualizar testes de controller e contrato HTTP
  - Files / modules: `backend/src/test/java/com/dockflow/dockflow/document/controller/DocumentControllerTest.java`, contrato OpenAPI
  - Verification: request válido, tamanho inconsistente, validações, indisponibilidade, resultado inconclusivo e resposta de sucesso são verificados conforme OpenAPI.

- [ ] T-022 [AC-008, NFR-004, NFR-005, NFR-006] Verificar segurança, observabilidade e health/readiness
  - Files / modules: testes de configuração/Actuator a criar, `specs/05-security/document-upload/`, `specs/09-operations/document-upload/`
  - Verification: secrets e bytes não aparecem em logs/respostas; métricas e traces possuem correlação; health/readiness detecta dependências essenciais; alertas apontam para runbook.

- [ ] T-023 [AC-001, AC-002, AC-003, AC-004, AC-005, AC-006, AC-007, AC-008] Executar validação de contrato e suíte Maven completa
  - Files / modules: `specs/04-contracts/document-upload/openapi.yaml`, `specs/04-contracts/document-upload/data-model.md`, projeto `backend/`
  - Verification: validação de contratos, lint, testes unitários, integração, testes de storage, security checks e build passam sem divergências.

## Phase 5 — Delivery

- [ ] T-024 [AC-008, NFR-006, NFR-007] Atualizar observabilidade, dashboards, alertas e runbook operacional
  - Files / modules: `specs/09-operations/document-upload/observability.md`, `specs/09-operations/document-upload/runbook.md`, configuração de observabilidade do backend
  - Verification: operadores conseguem detectar indisponibilidade, erro elevado, latência, estado preso e conteúdo órfão; cada alerta possui owner e procedimento.

- [ ] T-025 [FR-006, FR-007, NFR-007] Documentar deployment, rollback e forward recovery
  - Files / modules: `specs/08-release/document-upload/deployment.md`, `specs/08-release/document-upload/release-plan.md`, `specs/06-plans/document-upload/migrations.md`
  - Verification: migration, MinIO, bucket, configuração, abort/rollback, reconciliação e recuperação de objetos órfãos são executáveis e revisados.

- [ ] T-026 [FR-001, FR-004, FR-006, NFR-008] Executar revisão de convergência antes do merge
  - Files / modules: `specs/07-verification/convergence-checklist.md`, spec, architecture, ADR, plan, tasks, contracts, código e testes
  - Verification: `SPEC ↔ PLAN ↔ TASKS ↔ CODE ↔ TESTS ↔ CONTRACTS` está alinhado; toda divergência intencional está documentada e possui follow-up.

- [ ] T-027 [NFR-004, NFR-006, NFR-007, NFR-008] Concluir revisão de produção e PR
  - Files / modules: `specs/08-release/document-upload/production-readiness.md`, PR da feature
  - Verification: code review concluído, CI verde, readiness aprovada ou condições resolvidas, ownership definido e nenhum bloqueio crítico aberto.

## Deferred

- [ ] Implementar processamento assíncrono com RabbitMQ.
- [ ] Implementar download, compartilhamento ou distribuição de documentos.
- [ ] Implementar OCR, conversão, classificação ou análise de conteúdo.
- [ ] Implementar versionamento, deduplicação, retenção automática ou varredura antivírus.
- [ ] Substituir MinIO por outro provider.
