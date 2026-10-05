# Tarefas — Recebimento de conteúdo binário no registro de documentos

Plano relacionado: specs/04-plano/entrada-binaria-documentos/plano.md

Documento histórico: esta rodada foi consolidada em
`specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md`.
Não usar estas tarefas como plano de execução da feature consolidada.

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente pra revisar de uma vez, referenciar o
requisito (FR-XXX) que ela implementa, e dizer como verificar que ficou pronta.

## Fase 1 — Base

- [x] T-001 [FR-001][FR-006][FR-009][FR-013] Confirmar a atualização de escopo e registrar, se aprovado, o ADR sobre staging separado, coordenação PostgreSQL–MinIO–RabbitMQ e quebra do contrato JSON.
  - Arquivos/módulos: `specs/01-produto/`, `specs/02-arquitetura/`, `specs/02-arquitetura/DECISAO/` e documentação da feature de mensageria.
  - Verificação: revisão documental confirma que produto, arquitetura, Spec e plano descrevem o mesmo fluxo; nenhum limite alterado fica sem decisão aprovada.

- [x] T-002 [FR-002][FR-004][FR-005][FR-011] Adicionar e fixar as dependências de Apache Commons IO e Apache Tika necessárias para `CountingInputStream` e `TikaInputStream`.
  - Arquivos/módulos: `backend/pom.xml`.
  - Verificação: Maven resolve as dependências, a aplicação compila e não é introduzida dependência de `byte[]` ou armazenamento temporário na composição do contrato.

- [x] T-003 [FR-009][FR-010] Definir a porta provider-neutral de staging dentro do limite de storage existente e seu adaptador MinIO com bucket `docflow-staging` separado.
  - Arquivos/módulos: `document/storage/` atual ou `document/port/out/storage/` e `document/adapter/out/storage/minio/` conforme a decisão arquitetural aprovada.
  - Verificação: a assinatura da porta, ownership do `InputStream`, referência retornada e falhas estão documentados; nenhuma classe de aplicação importa `io.minio`.

- [x] T-004 [FR-008][FR-012] Definir os códigos estáveis dos erros de entrada e o mapeamento para `ProblemDetail`, incluindo `invalidField` quando aplicável.
  - Arquivos/módulos: `document/exception/`, `GlobalExceptionHandler.java` e Spec da feature.
  - Verificação: uma tabela de erros cobre ausência de `file`, `Content-Type` vazio, assinatura divergente/não identificada, conteúdo vazio e excesso de tamanho sem envelope adicional.

- [x] T-005 [FR-006][FR-010][FR-013] Alinhar a porta de publicação com a feature de mensageria e confirmar que a mensagem recebe apenas `contentReference`, `documentId`, `sizeBytes` e `contentType` necessários ao contrato aprovado.
  - Arquivos/módulos: `document/application/`, `document/messaging/rabbitmq/` e Spec/plano de mensageria.
  - Verificação: teste ou contrato compartilhado confirma que o publisher é chamado somente depois do staging e nunca recebe `InputStream` ou binário.

## Fase 2 — Lógica principal

- [x] T-006 [FR-002][FR-003][FR-004] Criar o contrato interno `DocumentContent` e o DTO próprio da requisição multipart, mantendo `MultipartFile` restrito ao controller/adaptador de entrada.
  - Arquivos/módulos: `document/adapter/in/web/dto/` ou `document/dto/` atual, e `document/application/` conforme a organização aprovada.
  - Verificação: teste de mapeamento confirma `InputStream`, `filename` e `contentType`; inspeção de imports confirma que a aplicação não depende de Spring Web.

- [x] T-007 [FR-005][FR-011] Implementar a validação do conteúdo com `TikaInputStream.get(stream)`, comparação com o `Content-Type` declarado e limite máximo de `52428800` bytes.
  - Arquivos/módulos: validador interno de conteúdo em `document/application/` ou `document/domain/`, dependências Tika/Commons IO e exceções de entrada.
  - Verificação: testes cobrem assinatura compatível, divergente, não identificada, conteúdo vazio, `Content-Type` ausente e tamanho acima/igual ao limite.

- [x] T-008 [FR-009][FR-010][FR-011] Implementar a gravação do stream no bucket `docflow-staging` usando `CountingInputStream`, produzindo a referência lógica `staging/{documentId}` e fechando o stream após a escrita.
  - Arquivos/módulos: porta de staging, `MinioDocumentStorage` ou adaptador de staging aprovado, configuração MinIO e `DocumentContent`.
  - Verificação: teste verifica bytes gravados, contagem igual ao conteúdo escrito, bucket separado, referência retornada e fechamento do stream mesmo em sucesso/falha.

- [x] T-009 [FR-006][FR-007][FR-013] Atualizar o Application Service para orquestrar a validação, persistência do registro conforme o fluxo existente, staging e publicação antes da resposta HTTP.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` ou equivalente em `document/application/`, `DocumentRepository` e portas de staging/publicação.
  - Verificação: teste unitário confirma a ordem aprovada, documento em `PENDING`, `sizeBytes` contado e que o publisher não é chamado quando a validação ou o staging falha.

- [x] T-010 [FR-006][FR-010][FR-013] Integrar a referência do staging ao publisher da mensageria e manter a requisição aberta até a publicação concluída.
  - Arquivos/módulos: Application Service, porta de publicação e contrato `DocumentStorageRequested` da feature de mensageria.
  - Verificação: teste verifica que a mensagem não contém binário/stream, contém `contentReference` e que a resposta não é concluída antes do publisher confirm aprovado.

- [x] T-011 [FR-005][FR-008][FR-012] Implementar o tratamento de falhas de validação e staging sem publicação, com resposta `400` em `application/problem+json`.
  - Arquivos/módulos: exceções de conteúdo, `GlobalExceptionHandler.java` e mapeamento de `ProblemDetail`.
  - Verificação: cada erro produz `status = 400`, `errorCode` textual estável e `invalidField` quando aplicável; nenhuma mensagem é publicada.

## Fase 3 — Interface

- [x] T-012 [FR-001][FR-002][FR-003] Alterar `DocumentController.registerDocument` para receber `multipart/form-data` com a parte obrigatória `file` e traduzir o request para `DocumentContent`.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java` e DTO multipart.
  - Verificação: teste MVC confirma que o controller chama a aplicação com contrato próprio e não repassa `MultipartFile`.

- [x] T-013 [FR-007] Remover o caminho JSON somente com metadados do `POST /documents` e atualizar a documentação do contrato quebrado.
  - Arquivos/módulos: controller, DTO `DocumentRegistrationRequest.java`, documentação OpenAPI/docs e testes existentes.
  - Verificação: request JSON recebe `400`; request multipart válido segue o fluxo; `GET /documents/{id}` e demais contratos não relacionados continuam funcionando.

- [x] T-014 [FR-005][FR-009][FR-013] Configurar o limite de upload de `52428800` bytes e o bucket `docflow-staging` por ambiente, sem credenciais hardcoded.
  - Arquivos/módulos: `backend/src/main/resources/application.properties`, `infra/.env.example`, `infra/docker-compose.yml` e inicialização/configuração do MinIO já existente.
  - Verificação: ambiente local inicia com configuração externa, o bucket separado pode ser validado/criado pelo procedimento documentado e upload acima do limite é rejeitado.

- [x] T-015 [FR-008][FR-012] Integrar o handler global à resposta RFC 7807 sem criar envelope adicional.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/exception/GlobalExceptionHandler.java` e testes MVC.
  - Verificação: respostas `400` têm `Content-Type: application/problem+json`, campos padrão de `ProblemDetail`, `errorCode` e `invalidField` quando aplicável.

## Fase 4 — Testes

- [x] T-016 [FR-001][FR-002][FR-003][FR-004] Cobrir o mapeamento do multipart para `DocumentContent` e a ausência de dependência Spring Web na aplicação.
  - Arquivos/módulos: testes do DTO/controller e `backend/src/test/java/com/dockflow/dockflow/document/`.
  - Verificação: testes confirmam `file`, `filename`, `Content-Type`, `InputStream` e rejeitam qualquer tentativa de usar o DTO JSON anterior para binário.

- [x] T-017 [FR-005][FR-011] Cobrir Tika, `TikaInputStream`, `CountingInputStream`, limite e ownership do stream.
  - Arquivos/módulos: testes unitários do validador e do componente de staging.
  - Verificação: compatível aceita; divergente/não identificada, vazio e acima de `52428800` rejeitam com `400`; contagem e fechamento são confirmados.

- [x] T-018 [FR-006][FR-009][FR-010][FR-013] Cobrir a orquestração staging → publicação → resposta e suas falhas.
  - Arquivos/módulos: `DocumentServiceTest` ou testes da camada de aplicação, com portas falsas/mocks.
  - Verificação: publisher só é chamado após staging; falha de staging não publica; mensagem carrega somente referência; resposta aguarda as duas etapas.

- [x] T-019 [FR-001][FR-005][FR-007][FR-008][FR-012] Atualizar `DocumentControllerTest` para multipart e erros RFC 7807.
  - Arquivos/módulos: `backend/src/test/java/com/dockflow/dockflow/document/controller/DocumentControllerTest.java`.
  - Verificação: caminho feliz retorna `201` conforme contrato atual de resposta; JSON antigo, ausência de `file`, headers inválidos e conteúdo inválido retornam `400` com propriedades esperadas.

- [x] T-020 [FR-009][FR-010] Testar o adaptador MinIO e o bucket de staging separado.
  - Arquivos/módulos: `MinioDocumentStorageTest.java` ou novo teste do adaptador de staging; configuração de integração MinIO.
  - Verificação: escrita usa `docflow-staging`, referência é `staging/{documentId}`, o cliente existente é reutilizado e falhas não são convertidas em sucesso.

- [x] T-021 [FR-006][FR-010] Executar o teste de contrato/integração da publicação RabbitMQ conforme o plano da feature de mensageria.
  - Arquivos/módulos: `document/messaging/rabbitmq/`, testes de contrato e infraestrutura de teste aprovada.
  - Verificação: mensagem contém referência e metadados corretos, nunca binário, e o publisher confirm é respeitado.

- [x] T-022 [FR-003][FR-007][FR-013] Executar regressão do backend existente e revisar a convergência da entrega.
  - Arquivos/módulos: suíte em `backend/src/test/java/`, persistência PostgreSQL, `GET /documents/{id}` e documentação.
  - Verificação: testes de persistência, consulta, estados e contexto da aplicação passam; qualquer mudança intencional no contrato JSON está registrada.

## Fase 5 — Entrega

- [x] T-023 [FR-001][FR-005][FR-006][FR-009][FR-013] Atualizar documentação de configuração, bucket, limite, mensageria e operação do fluxo.
  - Arquivos/módulos: `docs/`, `infra/.env.example`, configuração aplicável e Specs relacionadas.
  - Verificação: outra pessoa consegue configurar o bucket e executar o fluxo sem procurar credenciais ou decisões fora da documentação.

- [x] T-024 [FR-001][FR-007][FR-009][FR-013] Revisar rollback da quebra de contrato e de falhas entre PostgreSQL, MinIO e RabbitMQ.
  - Arquivos/módulos: procedimento de entrega/rollback, documentação de staging e mensagens.
  - Verificação: procedimento define como voltar ao código anterior sem alteração manual destrutiva e como identificar objetos/mensagens pendentes.

- [x] T-025 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013] Revisar diff, riscos, critérios de aceite e aprovação final antes de qualquer deploy.
  - Arquivos/módulos: Spec, plano, tarefas, código, testes e ADRs aprovados.
  - Verificação: checklist `Spec ↔ Plano/Tarefas ↔ Código ↔ Teste` concluído, riscos residuais registrados e aprovação de Marcos obtida.

## Adiado (fora do escopo desta rodada)

- [ ] Alterar a máquina de estados além da criação existente em `PENDING`.
- [ ] Implementar o consumidor RabbitMQ, armazenamento final, confirmação final ou limpeza do staging; essas responsabilidades permanecem nas specs próprias.
- [ ] Criar outbox, transação distribuída ou novo estado persistido para coordenar PostgreSQL, MinIO e RabbitMQ.
- [ ] Implementar política antivírus, autenticação/autorização, retenção ou lifecycle automático além da configuração aprovada do bucket.
- [ ] Migrar todo o backend para a organização arquitetural alvo; somente componentes necessários desta feature serão ajustados.
