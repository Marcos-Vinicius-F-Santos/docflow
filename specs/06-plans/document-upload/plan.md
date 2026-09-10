# Implementation Plan — Armazenamento binário de documentos

Status: Draft  
Owner:  
Related spec: [document-upload](../../02-product/document-upload/spec.md)  
Related architecture: [document-upload architecture](../../03-architecture/document-upload/architecture.md)  
Related ADRs: [ADR-001 — Armazenamento binário fora do PostgreSQL](../../01-decisions/ADR/ADR-001-document-binary-storage.md)

## 1. Technical Summary

Estender o caso de uso de registro de documentos para receber conteúdo binário, armazená-lo no MinIO e persistir no PostgreSQL uma referência estável ao objeto.

O fluxo manterá os boundaries atuais:

```text
API → DocumentService → DocumentStorage → MinioDocumentStorage → MinIO
                         ↓
                    DocumentRepository → PostgreSQL
```

O `DocumentService` continuará coordenando o caso de uso. O domínio não importará o SDK do MinIO. O PostgreSQL continuará armazenando metadados, estado e referência; os bytes permanecerão no MinIO.

**Rastreabilidade:** Feature Spec `FR-001`–`FR-009`, `NFR-001`–`NFR-008`; Architecture Delta — Components/Boundaries, Persistence e Data Flow; `ADR-001`.

## 2. Existing System Impact

O código atual registra somente metadados por meio de `DocumentController`, `DocumentService` e `DocumentRepository`. `DocumentStorage` existe como interface, mas não está integrado, e `MinioDocumentStorage` ainda é um esqueleto.

Impactos esperados:

- o contrato de registro deixará de aceitar apenas JSON de metadados;
- `Document` passará a manter uma referência ao conteúdo e um controle de concorrência;
- o service terá transições explícitas `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`;
- o schema ganhará campos compatíveis com registros legados;
- o Compose passará a disponibilizar MinIO e seu bucket corretamente;
- os testes atuais precisarão preservar os cenários existentes e incluir storage real/mocado;
- o contrato de consulta continuará sem expor detalhes sensíveis do provider por padrão.

**Rastreabilidade:** `NFR-008`; Architecture Delta — Compatibility Impact; `ADR-001` Consequences.

## 3. Proposed Implementation

### 3.1 Contratos e decisões pendentes

Antes de alterar o código, criar e aprovar os contratos da feature para definir:

- formato HTTP para metadados e conteúdo binário;
- categorias e payloads de erro;
- limite máximo de tamanho;
- tipos de conteúdo permitidos;
- comportamento para conteúdo vazio;
- política de retry, resultado inconclusivo e reconciliação;
- metas de latência, disponibilidade e capacidade.

Esses pontos estão explicitamente abertos na Feature Spec e no Architecture Delta. A implementação não deve escolher valores silenciosamente.

**Rastreabilidade:** Feature Spec — Open Questions; Architecture Delta — Open Decisions for Plan / Contracts; Constitution — contratos explícitos e não inventar requisitos.

### 3.2 Persistência e estados

Adicionar ao modelo `Document` uma referência provider-neutral ao objeto, denominada `objectKey` na arquitetura, e um mecanismo persistente de controle de concorrência.

O fluxo será:

1. validar metadados e conteúdo;
2. criar o documento em `PENDING` com identidade estável;
3. transicionar para `PROCESSING` de forma condicional;
4. armazenar o conteúdo usando referência estável gerada pelo servidor;
5. após confirmação, persistir a referência e `COMPLETED`;
6. em falha conhecida, persistir `FAILED`;
7. em resultado inconclusivo, não declarar sucesso e manter o caso elegível para retry/reconciliação.

O mecanismo de concorrência escolhido para o plano é versionamento otimista no documento, pois atende ao boundary já definido sem criar locking de aplicação ou novo serviço. A referência não será fornecida livremente pelo cliente e não dependerá exclusivamente do nome original.

**Rastreabilidade:** `FR-004`, `FR-006`, `FR-009`, `NFR-002`, `NFR-007`; Architecture Delta — Persistence/Concurrency; `ADR-001` Decision itens 5–8.

### 3.3 Storage

Completar a porta `DocumentStorage` para expressar apenas operações provider-neutral necessárias ao armazenamento confirmado, retry e compensação/reconciliação.

Implementar o adapter MinIO com:

- cliente MinIO isolado no adapter;
- endpoint, bucket e credenciais configuráveis;
- tradução de erros do provider para categorias estáveis;
- confirmação explícita da escrita;
- suporte às operações necessárias para verificar, repetir ou compensar uma tentativa.

Nenhum tipo do SDK do MinIO deve atravessar para `Document`, `DocumentService`, controllers ou DTOs.

**Rastreabilidade:** `NFR-003`, `FR-003`, `FR-005`, `FR-007`; Architecture Delta — Storage Boundary; `ADR-001` Decision itens 2–4.

### 3.4 Consistência entre banco e storage

Não manter uma transação de banco aberta durante toda a transferência. As alterações de estado e referência serão transacionais no PostgreSQL; a chamada ao MinIO será coordenada pelo service fora de uma transação distribuída.

Se o MinIO confirmar e a finalização no banco falhar, o caso não será reportado como sucesso. A referência estável permitirá retry/verificação e o runbook deverá cobrir reconciliação e eventual conteúdo órfão.

**Rastreabilidade:** `FR-006`, `FR-007`, `FR-009`, `NFR-007`; Architecture Delta — Data Flow/Runtime Scenarios; `ADR-001` Decision itens 7–8.

## 4. Components to Change

| Componente | Arquivo | Mudança planejada | Risco |
|---|---|---|---|
| Domínio | `backend/src/main/java/com/dockflow/dockflow/document/Document.java` | Adicionar referência ao objeto, controle de concorrência e transições protegidas | Alto |
| Aplicação | `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` | Orquestrar validação, estados, storage, finalização e falhas | Alto |
| Persistência | `backend/src/main/java/com/dockflow/dockflow/document/DocumentRepository.java` | Adicionar consultas/transições condicionais somente se necessárias ao controle de concorrência | Médio |
| Porta de storage | `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java` | Completar contrato provider-neutral | Alto |
| Adapter MinIO | `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorage.java` | Implementar integração com MinIO e mapeamento de erros | Alto |
| Configuração | novo `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageProperties.java` | Centralizar configurações externalizadas | Médio |
| Configuração | novo `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageConfiguration.java` | Criar cliente/beans do adapter sem vazar SDK | Médio |
| Erros | novo `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorageException.java` | Representar falhas provider-neutral | Médio |
| API | `backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java` | Receber o contrato de conteúdo e delegar somente ao service | Alto |
| API | `backend/src/main/java/com/dockflow/dockflow/document/dto/DocumentRegistrationRequest.java` | Substituir o request metadata-only pelo contrato aprovado | Alto |
| API | `backend/src/main/java/com/dockflow/dockflow/document/exception/GlobalExceptionHandler.java` | Mapear categorias de erro sem expor detalhes do provider | Médio |
| Build | `backend/pom.xml` | Adicionar SDK MinIO e dependências de teste necessárias | Médio |
| Configuração | `backend/src/main/resources/application.properties` | Adicionar propriedades provider-neutral/externalizadas do storage | Médio |
| Infraestrutura | `infra/docker-compose.yml` | Corrigir serviços/volumes e disponibilizar MinIO funcional | Alto |
| Configuração de exemplo | `infra/.env.example` | Declarar variáveis sem credenciais de produção | Médio |

`DocumentResponse` e `DocumentMapper` devem ser revisados durante a implementação. A decisão padrão é manter a referência do provider privada e expor identidade, metadados e estado; qualquer exposição de referência provider-neutral exige alteração explícita do contrato.

**Rastreabilidade:** Architecture Delta — Components/Boundaries/Contracts/Security; `NFR-003`, `NFR-004`, `NFR-008`.

## 5. New Components / Artifacts

### Código

- `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageProperties.java`
- `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioStorageConfiguration.java`
- `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorageException.java`

Os arquivos `DocumentStorage.java` e `MinioDocumentStorage.java` já existem no working tree como base da DOC-006 e devem ser tratados como arquivos a alterar, preservando mudanças locais não relacionadas.

### Contratos e dados

- `specs/04-contracts/document-upload/openapi.yaml`
- `specs/04-contracts/document-upload/data-model.md`
- `specs/05-security/document-upload/security-requirements.md`
- `specs/05-security/document-upload/threat-model.md`

Não será criado AsyncAPI para esta feature porque RabbitMQ está fora do escopo.

### Verificação e operação

- `specs/07-verification/document-upload/test-plan.md`
- `specs/09-operations/document-upload/observability.md`
- `specs/09-operations/document-upload/runbook.md`
- `backend/src/test/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorageIntegrationTest.java`

**Rastreabilidade:** `FR-003`, `FR-007`, `NFR-004`–`NFR-007`; Architecture Delta — Contracts/Operational Impact/Testing Implications; Constitution — segurança, verificação e operabilidade.

## 6. API / Event Changes

### API

O contrato lógico de registro deverá aceitar:

- metadados existentes;
- conteúdo binário;
- tamanho declarado;
- tipo de conteúdo;
- resultado distinguível entre sucesso, falha conhecida e resultado não confirmado.

O contrato atual JSON-only é insuficiente. O formato de transporte será definido no OpenAPI antes da alteração do controller/DTO.

O endpoint de consulta existente deve continuar compatível para documentos legados. A resposta deve informar estado e metadados sem expor credenciais ou detalhes internos do MinIO.

Erros devem ser provider-neutral e documentar, no mínimo:

- entrada inválida;
- tamanho/conteúdo inconsistente;
- storage indisponível;
- storage rejeitado;
- resultado não confirmado;
- falha na finalização persistente;
- conflito por operação concorrente.

### Eventos

Nenhum evento ou canal RabbitMQ será criado nesta feature.

**Rastreabilidade:** `FR-001`, `FR-002`, `FR-005`–`FR-009`, `NFR-008`; Architecture Delta — Contracts/Rejected Alternatives; `ADR-001` item 9.

## 7. Data / Migration Changes

Criar uma migration posterior à `V2__create_documents_table.sql`, por exemplo:

`backend/src/main/resources/db/migration/V3__add_document_storage_metadata.sql`

A migration deverá:

- adicionar referência nullable para preservar registros legados;
- adicionar coluna de versão de concorrência com valor inicial compatível;
- garantir unicidade da referência quando presente;
- impedir estado `COMPLETED` sem referência persistida;
- manter as constraints existentes de status, nome, tipo e tamanho;
- documentar compatibilidade e recuperação/rollback conforme a política de migrations.

Os registros atuais sem conteúdo continuarão consultáveis e não receberão referência fictícia. A migration deve ser expand-only e compatível com o código anterior durante a janela de rollout definida.

O modelo de dados deverá registrar:

- que bytes não ficam no PostgreSQL;
- ciclo de vida da referência;
- classificação de privacidade;
- comportamento para conteúdo órfão;
- estratégia de forward recovery.

**Rastreabilidade:** `FR-004`, `FR-008`, `NFR-002`, `NFR-007`, `NFR-008`; Architecture Delta — Persistence/Compatibility Impact; Constitution — migrations e compatibilidade.

## 8. Security Changes

- Externalizar endpoint, bucket e credenciais do MinIO.
- Remover qualquer dependência de credenciais padrão para produção.
- Manter o bucket privado.
- Gerar a referência no servidor; não aceitar path arbitrário do cliente.
- Definir limite máximo de upload e tipos de conteúdo antes da implementação.
- Validar tamanho declarado e conteúdo recebido.
- Não registrar bytes, secrets ou dados sensíveis em logs/traces.
- Mapear falhas do provider sem devolver detalhes internos ao consumidor.
- Validar autenticação e autorização de forma independente; a feature não cria um novo modelo de identidade.

Os requisitos de segurança e o threat model devem ser aprovados antes do código de integração.

**Rastreabilidade:** `NFR-004`, `NFR-005`, `FR-005`, regras de negócio da Feature Spec; Architecture Delta — Security; Constitution — Security.

## 9. Observability Changes

Instrumentar o fluxo para medir:

- tentativas;
- sucessos;
- falhas conhecidas por categoria;
- resultados inconclusivos;
- latência;
- volume de bytes;
- operações em reconciliação;
- conteúdo órfão e compensações malsucedidas.

Adicionar ou atualizar:

- health/readiness do PostgreSQL e MinIO/bucket;
- logs estruturados com document ID, operation/correlation ID e categoria do erro;
- traces separados para API, banco e storage;
- dashboard e alertas para indisponibilidade, erro elevado, latência e estados presos.

**Rastreabilidade:** `US-004`, `FR-007`, `AC-005`, `AC-008`, `NFR-006`, `NFR-007`; Architecture Delta — Observability/Operational Impact; Constitution — Operability.

## 10. Implementation Sequence

### Phase 1 — Foundation and contract closure

1. Confirmar a aprovação da Feature Spec no artefato correspondente antes do início do código.
2. Resolver as questões abertas de formato HTTP, tamanho, conteúdo vazio, tipos aceitos, erros, retry/reconciliação e metas operacionais.
3. Criar/aprovar OpenAPI, modelo de dados, security requirements e threat model.
4. Criar o test plan, observability spec e runbook como artefatos de execução.

**Gate:** nenhum contrato ou requisito `Must` pendente sem premissa documentada.

**Rastreabilidade:** Feature Spec — Open Questions/Release Criteria; Constitution — specs como contratos vivos.

### Phase 2 — Infrastructure and dependencies

1. Corrigir a estrutura real do `infra/docker-compose.yml`.
2. Disponibilizar PostgreSQL e MinIO, volumes persistentes e bucket necessário.
3. Externalizar credenciais e propriedades sem versionar secrets.
4. Adicionar a dependência do SDK MinIO e as dependências de container necessárias.
5. Definir health/readiness do storage.

**Gate:** Compose validado, MinIO acessível com credenciais não hardcoded e bucket disponível.

**Rastreabilidade:** `FR-003`, `NFR-004`, `NFR-006`; Architecture Delta — Integration/Security/Operational Impact; `ADR-001` itens 1–4.

### Phase 3 — Persistence and domain

1. Criar a migration `V3` para referência, versão e constraints.
2. Atualizar `Document` sem setters públicos artificiais.
3. Encapsular transições de estado no domínio/service.
4. Implementar o controle de concorrência escolhido.
5. Validar leitura de registros legados sem referência.

**Gate:** migrations executam sem alteração destrutiva e `COMPLETED` não pode existir sem referência.

**Rastreabilidade:** `FR-002`, `FR-004`, `FR-008`, `FR-009`, `NFR-002`, `NFR-008`; Architecture Delta — Persistence/Concurrency; Constitution — Data/Code Quality.

### Phase 4 — Storage port and MinIO adapter

1. Finalizar o contrato provider-neutral de `DocumentStorage`.
2. Criar configuração tipada do MinIO.
3. Implementar `MinioDocumentStorage`.
4. Mapear erros, confirmações e operações de retry/compensação.
5. Garantir que o SDK não atravesse o boundary.

**Gate:** adapter testado isoladamente e contra MinIO real, com integridade de bytes e metadata.

**Rastreabilidade:** `FR-003`, `FR-005`, `FR-007`, `NFR-001`, `NFR-003`; Architecture Delta — Storage Boundary; `ADR-001` opção 3.

### Phase 5 — Application and API

1. Atualizar o comando de registro para o contrato aprovado.
2. Atualizar `DocumentService` com o fluxo de estados e as fronteiras transacionais.
3. Usar referência estável gerada no servidor.
4. Finalizar documento somente após confirmação do MinIO e persistência da referência.
5. Atualizar controller, DTOs e handler de erros.
6. Preservar o contrato de consulta e evitar exposição do provider.

**Gate:** nenhum caminho retorna sucesso prematuro; erros conhecidos e inconclusivos são distinguíveis.

**Rastreabilidade:** `FR-001`–`FR-009`, `AC-001`–`AC-007`, `NFR-002`, `NFR-008`; Architecture Delta — Data Flow/Contracts; `ADR-001` itens 5–8.

### Phase 6 — Verification

1. Atualizar testes unitários do domínio e service.
2. Atualizar testes MockMvc para conteúdo, validações e erros.
3. Expandir integração para PostgreSQL + MinIO reais em containers.
4. Testar integridade, tamanho, tipo, retries, resultado inconclusivo e falha parcial.
5. Testar concorrência por documento e paralelismo entre documentos distintos.
6. Validar OpenAPI e modelo de dados contra a implementação.
7. Validar segurança, observabilidade e health/readiness.

**Gate:** todos os requisitos `Must`, critérios de aceitação e cenários de falha têm evidência; suíte Maven completa verde.

**Rastreabilidade:** `AC-001`–`AC-008`; Architecture Delta — Testing Implications; Constitution — Testing/Integrity.

### Phase 7 — Delivery readiness

1. Atualizar deployment/rollback/forward recovery.
2. Revisar dashboard, alertas e runbook.
3. Executar convergence review entre spec, plan, tasks, código, testes e contratos.
4. Confirmar PR, revisão de código e ausência de divergências não documentadas.

**Gate:** production readiness aprovada ou condições explicitamente resolvidas antes do release.

**Rastreabilidade:** Feature Spec — Release Criteria; Architecture Delta — Operational/Compatibility Impact; Constitution — Delivery/Operability.

## 11. Dependencies

| Dependência | Uso | Bloqueia |
|---|---|---|
| MinIO SDK compatível com Java 21/Spring Boot | Implementação do adapter | Phase 4 |
| MinIO executável e bucket provisionado | Integração e operação | Phases 2, 4 e 6 |
| PostgreSQL/Flyway existentes | Persistência e migration | Phase 3 |
| Testcontainers | Teste integrado com dependências reais | Phase 6 |
| Contrato OpenAPI aprovado | Alteração da API | Phase 5 |
| Security requirements/threat model aprovados | Integração e release | Phases 2, 5 e 7 |
| RabbitMQ | Não utilizado nesta feature | Não aplicável |

## 12. Risks

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| Não existe atomicidade entre PostgreSQL e MinIO | Alta | Alto | Estados explícitos, referência estável, retry, compensação e reconciliação; `COMPLETED` somente após finalização no banco |
| Conteúdo órfão após falha de finalização | Média | Alto | Chave estável, operação de compensação/reconciliação, alerta e runbook |
| Retry cria associação duplicada ou sobrescreve conteúdo | Média | Alto | Controle de concorrência e referência determinística/estável por documento |
| API atual é incompatível com conteúdo binário | Alta | Alto | Contract-first, migração/versionamento deliberado e testes de contrato |
| Upload grande causa exaustão de memória/recursos | Média | Alto | Limite explícito, streaming e testes de capacidade definidos antes da implementação |
| Credenciais ou conteúdo aparecem em logs/configuração | Média | Alto | Secrets externalizados, redaction e testes de segurança |
| Compose atual permanece inválido | Alta | Alto | Corrigir e validar infraestrutura na Phase 2 antes do adapter |
| Documentos legados não possuem referência | Alta | Médio | Campo nullable, leitura compatível e nenhuma alteração fictícia de dados |
| Estado permanece preso em `PROCESSING` | Média | Alto | Sinal de reconciliação, alerta, runbook e teste de recuperação |
| Mudanças locais da DOC-006 são sobrescritas | Média | Médio | Revisar working tree antes de editar e preservar alterações existentes |

## 13. Compatibility Strategy

- Manter o package `com.dockflow.dockflow`.
- Não alterar migrations existentes; adicionar uma nova migration versionada.
- Tornar a nova referência nullable para registros anteriores.
- Manter consultas de documentos legados funcionando.
- Não expor o SDK ou detalhes do MinIO no contrato público.
- Tratar a mudança do registro metadata-only para conteúdo binário como alteração contratual explícita, com estratégia de compatibilidade definida no OpenAPI.
- Não adicionar RabbitMQ nem alterar o fluxo de processamento assíncrono nesta story.

**Rastreabilidade:** `NFR-003`, `NFR-008`; Architecture Delta — Compatibility Impact; `ADR-001` Consequences.

## 14. Rollout Strategy

O rollout deve ser progressivo e reversível:

1. validar migration expand-only e infraestrutura MinIO sem ativar tráfego de upload;
2. validar health/readiness, bucket, métricas e alertas;
3. habilitar o novo fluxo para ambiente interno/teste;
4. observar integridade, erro, latência, estados presos e órfãos;
5. expandir o tráfego somente após os sinais definidos no release plan;
6. manter o procedimento de rollback/forward recovery para falhas de storage ou migration.

Como a operação envolve dois sistemas, rollback de aplicação não desfaz automaticamente objetos já gravados. O release plan deverá definir reconciliação e limpeza/forward recovery sem apagar dados indiscriminadamente.

**Rastreabilidade:** `FR-006`, `FR-007`, `NFR-007`; Architecture Delta — Runtime Scenarios/Operational Impact; Constitution — Delivery/Data.

## 15. Open Questions

Estas questões bloqueiam a implementação do contrato ou devem ser resolvidas antes do release:

- Qual será o formato HTTP final para conteúdo e metadados?
- Qual é o limite máximo de tamanho?
- Conteúdo vazio será aceito?
- Quais tipos de conteúdo são permitidos?
- Quais códigos HTTP e payloads representam erro conhecido, conflito e resultado inconclusivo?
- Qual política de retry/reconciliação será executada automaticamente e qual será operacional?
- Quais metas de latência, disponibilidade, capacidade e taxa de sucesso serão usadas?
- A referência provider-neutral será apenas interna ou também será exposta a algum consumidor?

Não iniciar a implementação de API enquanto essas decisões não estiverem registradas no contrato ou como premissas aprovadas.

## 16. Completion Gate

O plano só será considerado concluído quando:

- todos os `Must` requirements e critérios `AC-001`–`AC-008` tiverem evidência;
- migration, compatibilidade e recuperação forem validadas;
- integração com MinIO real estiver coberta;
- API e erros estiverem documentados em contrato;
- segurança, observabilidade, alertas e runbook estiverem prontos;
- a suíte Maven completa estiver verde;
- a convergência `SPEC ↔ PLAN ↔ TASKS ↔ CODE ↔ TESTS ↔ CONTRACTS` não tiver divergências não documentadas;
- PR, revisão e readiness de produção estiverem concluídos.
