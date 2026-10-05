# Plano de Implementação — Registro binário e armazenamento assíncrono de documentos

Spec relacionada: specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md  
Status: Rascunho

## 1. Resumo técnico

O `POST /documents` passará a aceitar somente `multipart/form-data` com a parte
obrigatória `file`. O adaptador web traduzirá a parte multipart para um contrato interno
com `InputStream`, filename e content type; `MultipartFile` ficará restrito à borda HTTP.

O Application Service validará o conteúdo com Apache Tika, contará os bytes durante a
leitura e gravará primeiro o stream no bucket de staging `docflow-staging`. Em seguida,
persistirá o documento como `PENDING` e publicará no RabbitMQ apenas a referência
`staging/{documentId}` e os metadados aprovados, aguardando confirmação síncrona antes de
aceitar o registro. Se a publicação não for confirmada, o documento permanecerá
`PENDING` e o staging será preservado.

O consumidor validará a mensagem, obterá o claim atômico do documento, lerá o staging por
uma porta provider-neutral, verificará a idempotência do storage final, gravará usando a
porta `DocumentStorage` e removerá o staging somente após confirmação. Mensagens inválidas
irão diretamente para a DLQ; falhas transitórias usarão as três filas de retry definidas
na Spec.

Os componentes novos permanecerão dentro do domínio `document`, seguindo a organização
por domínio, aplicação, portas, adaptadores e mensageria já definida em
`specs/02-arquitetura/ARQUITETURA.md`. Não haverá frontend, outbox, transação distribuída,
reconciliação de `RESULT_UNKNOWN` ou migration nesta feature.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java` | Trocar o binding JSON por multipart, traduzir a parte `file` e manter o controller como único ponto que conhece `MultipartFile`. | Alto: quebra intencionalmente o contrato JSON do `POST /documents`. |
| `backend/src/main/java/com/dockflow/dockflow/document/dto/DocumentRegistrationRequest.java` | Deixar de ser usado no registro público; não adicionar `MultipartFile` ao contrato da aplicação. | Médio/alto: testes e clientes atuais dependem do DTO JSON. |
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` | Mover ou adaptar incrementalmente para `document/application` e orquestrar validação, persistência, staging e publicação. | Alto: coordena PostgreSQL, MinIO e RabbitMQ sem transação distribuída. |
| `Document.java`, `DocumentStatus.java` e persistência de `document` | Reutilizar as transições existentes, o `objectKey` determinístico e o controle de versão para claim concorrente. | Alto: uma transição incorreta pode duplicar a gravação final ou bloquear documentos. |
| `document/storage/DocumentStorage.java` e `MinioDocumentStorage.java` | Preservar o significado de `store`, `exists` e `delete`; adicionar o staging como porta separada e adaptador MinIO no limite arquitetural alvo. | Alto: bucket, credenciais e falhas do staging não podem afetar silenciosamente o storage final. |
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentRepository.java` | Adaptar a persistência para o claim condicional e a lease, sem permitir confirmação concorrente. | Alto: atualizações perdidas ou lease incorreta comprometem a idempotência. |
| `backend/pom.xml` | Adicionar Apache Tika, Commons IO e dependências RabbitMQ/Testcontainers que ainda não existirem. | Médio: dependências e autoconfiguração podem afetar compilação e inicialização. |
| `backend/src/main/resources/application.properties`, `infra/.env.example` e `infra/docker-compose.yml` | Configurar limite de upload, bucket de staging, MinIO, RabbitMQ e parâmetros de retry sem versionar segredos. | Médio/alto: configuração incompleta pode habilitar um endpoint que não consegue concluir o fluxo. |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` a `V3__add_document_storage_metadata.sql` | Não editar as migrations existentes e não criar nova migration nesta feature. | Alto se houver alteração manual: quebra a reprodutibilidade e contraria a Spec aprovada. |
| `backend/src/test/java/com/dockflow/dockflow/document/` | Substituir os testes de entrada JSON do registro por multipart e ampliar a cobertura de staging, publisher, consumidor, concorrência e regressão. | Médio: a quebra de contrato é intencional, mas consulta, persistência e estados existentes não podem regredir. |
| `docs/`, `specs/05-verificacao/` e procedimento operacional | Documentar configuração, topologia, smoke test, rollback e checklist de convergência após a implementação. | Médio: sem documentação, uma falha entre PostgreSQL, MinIO e RabbitMQ pode ser tratada de forma destrutiva. |

A movimentação de classes para `document/application`, `document/port`,
`document/adapter` e `document/messaging/rabbitmq` será incremental e limitada aos
componentes tocados. Não será criada uma raiz genérica por tipo nem um módulo novo.

## 3. Componentes novos

- Contrato interno de conteúdo com `InputStream`, filename e content type, dentro do
  limite de aplicação; sem `MultipartFile`, `byte[]` ou arquivo temporário como contrato.
- Porta `DocumentStaging` para gravar, abrir e remover conteúdo por referência lógica,
  separada de `DocumentStorage`.
- Adaptador MinIO de staging usando o cliente existente e o bucket `docflow-staging`.
- Modelo da mensagem `DocumentStorageRequested` v1, contendo somente os seis campos da
  Spec: `schemaVersion`, `documentId`, `objectKey`, `contentReference`, `sizeBytes` e
  `contentType`.
- Porta de publicação e adaptador RabbitMQ com publisher confirms síncronos.
- Topologia da feature: exchange `docflow.document-storage`, fila principal,
  `docflow.document-storage.dlx`, `docflow.document-storage.dlq` e filas de retry de
  `5s`, `15s` e `60s`.
- Consumidor assíncrono com validação de schema, claim/lease, idempotência, leitura do
  staging, gravação final, limpeza condicional e acknowledgment.
- Códigos de erro e mapeamento `ProblemDetail` para validações HTTP `400`.
- Logs estruturados para publicação, início do consumo, sucesso e falha, sempre com
  `documentId`.

## 4. Mudança de dados/banco (se houver)

Não haverá migration nem alteração do schema PostgreSQL. As migrations V1, V2 e V3
permanecem intactas. O plano deve reutilizar os campos e mecanismos já existentes para
estado, `objectKey`, `updated_at` e controle de versão; o claim deve ser uma operação
condicional/atômica no PostgreSQL.

Antes do código, a implementação precisa comprovar que o modelo versionado suporta a
lease de `10000 ms` por tentativa e o takeover somente após expiração sem adicionar campo
novo. Se isso não for possível, a implementação deve parar nesse gate e registrar a
divergência na Spec antes de propor qualquer migration.

O rollback será operacional: preservar banco, objetos de staging e mensagens, parar a
versão promovida e voltar para uma versão de código compatível com o schema existente.
Não será usado `DELETE`, `DROP`, `docker compose down -v` ou edição manual do histórico do
Flyway como mecanismo de rollback.

## 5. Sequência de implementação

### Fase 1 — Base

1. Confirmar os contratos da Spec e da ADR-004, tratando os planos históricos de
   `entrada-binaria-documentos` e `contrato-mensageria-rabbitmq-documentos` apenas como
   referência, não como execução paralela.
2. Definir os contratos provider-neutral de conteúdo, staging e publicação, incluindo
   ownership/fechamento do `InputStream` e a referência `staging/{documentId}`.
3. Definir o schema JSON v1 e a topologia RabbitMQ, incluindo confirmação síncrona,
   acknowledgment, DLQ, retries e timeout de `3000 ms`.
4. Confirmar como o claim atômico e a lease de `10000 ms` serão representados usando o
   schema existente e o controle de versão, sem migration.
5. Fixar dependências, propriedades e variáveis de ambiente; o RabbitMQ fará parte do
   Docker Compose desta entrega; credenciais da aplicação e do MinIO devem continuar fora
   do código versionado.
6. Fixar códigos de erro da API, propriedades `errorCode`/`invalidField` e a ordem de
   falha observável para validação, staging, publicação e consumo.

**Saída da fase:** contratos e limites revisados, ausência de divergência arquitetural
não registrada e confirmação de que o schema atual é suficiente para o claim/lease.

### Fase 2 — Lógica principal

1. Criar o contrato interno de conteúdo e o mapeamento do request sem propagar classes de
   Spring Web para a aplicação.
2. Implementar validação de filename, content type, conteúdo vazio, assinatura real com
   Apache Tika e limite de `52428800` bytes.
3. Compor `CountingInputStream` com a leitura efetiva, preservando o stream sem
   bufferização integral e fechando-o no componente que conclui a gravação.
4. Implementar o adaptador de staging no bucket separado, com escrita, leitura e remoção
   por `contentReference` e tradução de falhas do SDK.
5. Atualizar o Application Service para executar a ordem aprovada: gerar o identificador,
   gravar o staging, persistir o documento como `PENDING`, publicar somente a referência e
   aguardar confirmação antes da resposta HTTP. Se a publicação não for confirmada, manter
   o registro `PENDING` e preservar o staging.
6. Implementar o contrato e o publisher RabbitMQ com mensagem persistente, routing key
   `document.storage.requested` e publisher confirm síncrono.
7. Implementar o consumidor: validar mensagem, obter claim, respeitar lease, verificar
   `exists(objectKey)`, abrir staging, chamar `DocumentStorage.store` e remover staging
   apenas após confirmação; se o objeto final já existir, não chamar `store` e preservar
   o staging para reconciliação.
8. Implementar retry limitado para falhas transitórias e envio direto à DLQ para mensagem
   inválida, sem confirmação prematura.
9. Adicionar logs estruturados nos quatro pontos definidos e garantir a correlação por
   `documentId`, sem registrar binário, credenciais ou conteúdo sensível.

**Saída da fase:** fluxo produtor-consumidor completo, provider-neutral nas camadas de
aplicação e com as regras de idempotência, concorrência, retry e limpeza atendidas.

### Fase 3 — Interface

1. Alterar o controller para `multipart/form-data` com uma única parte obrigatória `file`.
2. Rejeitar o POST JSON antigo e todas as entradas inválidas com HTTP `400` e
   `application/problem+json`, usando `ProblemDetail`.
3. Preservar o contrato de sucesso aprovado: `201 Created`, `Location`
   apontando para `/documents/{id}` e `DocumentResponse` com `id`, `originalFilename`,
   `contentType`, `sizeBytes` real, `status: PENDING`, `createdAt` e `updatedAt`. O
   `contentReference` permanece interno.
4. Registrar os beans/configurações de MinIO, staging, publisher e consumidor sem fazer
   controller ou Application Service importar SDKs externos.
5. Configurar no ambiente local o RabbitMQ e o bucket de staging separados do storage
   final, mantendo `infra/.env.example` sem valores secretos reais.
6. Atualizar a documentação da API e de operação, preservando `GET /documents/{id}` e os
   demais contratos fora do escopo.

**Saída da fase:** endpoint e consumidor executáveis no ambiente local, com wiring
completo e sem introduzir frontend ou novo endpoint.

### Fase 4 — Testes

1. Cobrir o controller e o contrato HTTP: caminho feliz multipart, `file` ausente,
   filename/content type inválidos, conteúdo vazio, assinatura incompatível, limite e
   POST JSON antigo.
2. Cobrir stream, Tika e contador: stream sem `mark/reset`, assinatura real, tamanho
   exato, limite máximo, excesso e fechamento em sucesso/falha.
3. Cobrir Application Service e staging: ordem das operações, referência correta, ausência
   de publicação após validação/staging falhar e preservação do staging após falha de
   confirmação.
4. Cobrir contrato e publisher: seis campos, ausência de binário, persistência da
   mensagem, routing key, confirmação, rejeição, timeout e broker indisponível.
5. Cobrir consumidor: mensagem inválida, leitura do staging, `exists` verdadeiro/falso,
   `store`, limpeza após confirmação, falhas sem limpeza prematura e acknowledgment.
6. Cobrir retries e concorrência: entrega inicial mais três retries nos atrasos aprovados,
   DLQ, claim único, duplicatas em `PROCESSING`/`COMPLETED` e takeover somente após a
   expiração da lease.
7. Executar integração real com PostgreSQL, MinIO e RabbitMQ isolados, o caminho feliz
   completo e a regressão mínima de registro, consulta e máquina de estados.

**Saída da fase:** critérios AC-001 a AC-008 comprovados por testes direcionados e
integração; divergências ficam registradas antes da entrega.

### Fase 5 — Entrega (deploy/rollback)

1. Documentar pré-condições, variáveis, criação/validação do bucket, topologia, smoke
   test, observabilidade e preservação de objetos/mensagens em falhas.
2. Confirmar que nenhuma migration foi criada ou alterada e que o procedimento de
   rollback funciona com o schema existente.
3. Revisar o diff final e preencher o checklist `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes`.
4. Executar build, testes e smoke test no ambiente local aprovado; não considerar health
   do backend suficiente sem validar staging, confirmação e consumo.
5. Aguardar a revisão e aprovação de Marcos antes de qualquer deploy.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| O JSON atual do `POST /documents` será substituído por multipart obrigatório. | Alta | Alto: clientes e testes existentes podem falhar imediatamente. | Tratar `FR-007` como breaking change explícito, testar HTTP `400`, atualizar documentação e preparar rollback por versão. Não introduzir compatibilidade paralela sem nova aprovação. | Não há nova ADR para o contrato já aprovado; sim se for escolhida compatibilidade, versionamento ou coexistência. |
| PostgreSQL, MinIO e RabbitMQ não formam uma transação distribuída. | Alta | Alto: falhas entre staging, persistência e publicação podem deixar registros ou objetos órfãos. | Confirmar ordem das operações, preservar staging em falhas, usar publisher confirm, logs correlacionáveis e runbook de recuperação. Não adicionar outbox/compensação nesta feature. | Não: já coberto por ADR-001 e ADR-004. Outbox ou compensação exigiria nova ADR. |
| O schema atual pode não oferecer uma forma correta de representar a lease de `10000 ms` sem migration. | Média | Alto: claim incorreto permite corrida ou takeover prematuro. | Validar o modelo no gate da Fase 1; usar somente estado/versão/campos existentes. Se faltar suporte, abrir a tarefa condicional de emergência T-004-E e atualizar a Spec antes de criar migration ou alterar locking. | Sim, se a solução exigir alterar schema, estado ou mecanismo de locking. |
| A confirmação RabbitMQ, TTL, retry e DLQ podem ter semântica diferente dos defaults do framework. | Média | Alto: perda silenciosa, retry infinito ou confirmação prematura. | Configurar explicitamente a topologia, testar com broker real/isolado e cobrir confirmação, timeout, DLQ e contagem de tentativas. | Não enquanto permanecer específica desta feature; sim se virar padrão compartilhado entre features. |
| Validação Tika e gravação compartilham um stream não reposicionável. | Média | Alto: os bytes usados para detectar assinatura podem não ser os bytes persistidos. | Compor a leitura de forma streaming, sem `byte[]`/arquivo temporário, e testar streams sem `mark/reset`, tamanho e fechamento. | Não; é detalhe de implementação dentro do contrato aprovado. |
| O bucket de staging ou as credenciais podem ser configurados de forma diferente do storage final. | Média | Alto: registro aceito sem staging durável ou mistura de objetos finais e intermediários. | Variáveis explícitas, bucket separado, validação na inicialização/smoke test e nenhum segredo hardcoded. | Não; ADR-004 já define a separação. |
| O worktree contém alterações locais e uma migration V4 não pertencentes ao escopo desta feature. | Alta | Médio/alto: arquivos não revisados podem ser confundidos com implementação pronta. | Planejar contra o código versionado, revisar o diff isolado e manter migrations V1–V3 intactas; não marcar código local como concluído. | Não; é controle de escopo e revisão. |

## 7. Decisões incorporadas e ponto pendente

- **Ordem operacional definida:** gerar o identificador, gravar o staging, persistir o
  documento como `PENDING`, publicar a mensagem e aguardar confirmação. Se a publicação
  não for confirmada, manter `PENDING` e preservar o staging para tratamento operacional.
- **Lease/claim:** ainda precisa ser verificado contra o schema e o código existentes na
  T-004. Se não houver suporte, executar somente após aprovação da tarefa condicional
  T-004-E e atualização da Spec.
- **Contrato de sucesso aprovado:** preservar `201 Created`, `Location` e o
  `DocumentResponse` atual, com `sizeBytes` real e `status: PENDING`; não expor a
  referência de staging.
- **Ambiente local:** RabbitMQ será incluído/configurado no Docker Compose desta entrega;
  o teste end-to-end não poderá substituir publisher confirm por mock.
