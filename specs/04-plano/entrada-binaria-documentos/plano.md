# Plano de Implementação — Recebimento de conteúdo binário no registro de documentos

Spec relacionada: specs/03-features/entrada-binaria-documentos/spec.md  
Status: Encerrado — consolidado em `registro-binario-e-armazenamento-assincrono-documentos`

## 1. Resumo técnico

O `POST /documents` deixará de receber `@RequestBody` JSON e passará a receber
`multipart/form-data` com uma única parte obrigatória `file`. O controller traduzirá o
`MultipartFile` para um contrato interno próprio com `InputStream`, `filename` e
`contentType`; esse tipo não atravessará para a aplicação como dependência do Spring Web.

O fluxo será assíncrono. Após validar a assinatura com Apache Tika, o Application Service
gravará o stream em staging durável no bucket MinIO `docflow-staging`, usando
`CountingInputStream` para obter o `sizeBytes`. O stream será fechado após a gravação e a
mensagem RabbitMQ carregará somente o `contentReference`. A resposta HTTP só será
retornada depois da gravação e da publicação.

Os novos componentes ficarão dentro do domínio `document`, usando os limites de aplicação,
portas e adaptadores já previstos. Não será criada uma raiz genérica nem haverá migration.
Antes do código, a separação da porta de staging e a atualização do escopo arquitetural
atual devem ser confirmadas.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java` | Trocar o binding JSON por multipart, traduzir a parte `file` e chamar o contrato interno. | Alto: altera o contrato público e pode quebrar clientes existentes. |
| `backend/src/main/java/com/dockflow/dockflow/document/dto/DocumentRegistrationRequest.java` | Deixar de ser usado como `@RequestBody`; o DTO atual não deve ser ampliado para receber `MultipartFile`. | Médio/alto: testes e chamadas existentes dependem da assinatura atual. |
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` | Orquestrar registro, staging e publicação sem receber tipos do Spring; preservar documento em `PENDING`. | Alto: coordena PostgreSQL, MinIO e RabbitMQ sem transação distribuída. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java` | Definir, em uma porta provider-neutral do limite de storage, a operação necessária para staging ou criar uma porta específica no mesmo limite. | Alto: altera um contrato arquitetural usado pelo adaptador e pela mensageria. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorage.java` | Reutilizar o `MinioClient` existente e configurar o bucket `docflow-staging` separado do bucket final. | Alto: configuração de bucket e falhas de storage podem afetar a integração atual. |
| `backend/src/main/java/com/dockflow/dockflow/document/exception/GlobalExceptionHandler.java` | Mapear falhas de validação para `ProblemDetail` com `errorCode` e `invalidField`. | Médio: respostas de erro atuais podem mudar. |
| `backend/pom.xml` | Adicionar Apache Commons IO, Apache Tika e somente as dependências de mensageria ainda não fornecidas pelo plano RabbitMQ. | Médio: dependências e autoconfiguração podem afetar compilação e inicialização. |
| `backend/src/main/resources/application.properties` e `infra/.env.example` | Configurar limite de upload, endpoint/credenciais existentes e bucket `docflow-staging`, sem segredos versionados. | Médio: configuração ausente pode impedir a inicialização. |
| `backend/src/test/java/com/dockflow/dockflow/document/` | Substituir os testes de registro JSON por multipart e adicionar testes de validação, stream, staging, publicação e regressão. | Médio: a quebra de contrato é intencional, mas o fluxo de consulta e persistência não pode regredir. |
| `specs/01-produto/`, `specs/02-arquitetura/` e ADRs relacionados | Atualizar o escopo somente após aprovação, pois os documentos atuais dizem que binário, MinIO e RabbitMQ ainda são futuros. | Alto: divergência documental pode invalidar a convergência Spec ↔ Plano ↔ Código ↔ Teste. |

Não será feita uma migração estrutural geral do backend. Os arquivos existentes só serão
movidos para `document/application`, `document/port` e `document/adapter` se isso for
necessário para os componentes tocados e tiver sido aprovado como parte do ajuste
arquitetural; novos componentes não criarão pastas paralelas.

## 3. Componentes novos

- DTO próprio para a requisição multipart, sem reutilizar `DocumentRegistrationRequest`.
- Contrato interno `DocumentContent`, livre de Spring Web, com `InputStream`, `filename` e
  `contentType`.
- Validação por assinatura usando Apache Tika e `TikaInputStream.get(stream)`.
- Uso de `CountingInputStream` para calcular o tamanho durante a gravação única no staging.
- Porta provider-neutral para gravação de staging, dentro do limite de storage existente,
  e adaptador MinIO usando o cliente já configurado.
- Configuração do bucket `docflow-staging`, separado do bucket final.
- Orquestração do Application Service para staging → publicação da referência → resposta.
- Mapeamento de erros `400` para `ProblemDetail`, com apenas `errorCode` e, quando
  aplicável, `invalidField` como propriedades extensíveis.
- Integração com a porta de publicação definida pela feature de mensageria; o contrato
  RabbitMQ e seus detalhes operacionais não serão duplicados neste plano.

## 4. Mudança de dados/banco (se houver)

Não haverá migration nem alteração do schema PostgreSQL. Os metadados existentes continuam
sendo persistidos no PostgreSQL; o conteúdo recebido será gravado no bucket MinIO
`docflow-staging` para sobreviver ao encerramento da requisição.

O rollback não deve editar dados diretamente nem remover objetos em massa. A reversão
deverá usar uma versão de código compatível e o procedimento aprovado para mensagens e
objetos de staging. A política para objetos órfãos quando staging ou publicação falhar deve
ser fechada antes da implementação, respeitando a ausência de transação distribuída dos
ADRs existentes.

## 5. Sequência de implementação

### Fase 1 — Base

1. Confirmar a mudança de fase nos documentos de produto/arquitetura e registrar ADR se
   necessário para a nova porta de staging, a configuração de bucket separado e a
   coordenação PostgreSQL–MinIO–RabbitMQ.
2. Definir a porta provider-neutral de staging no limite já existente, sem importar o SDK
   MinIO na aplicação, e alinhar sua referência com a feature de mensageria.
3. Adicionar Commons IO e Apache Tika e fechar a composição
   `TikaInputStream` + `CountingInputStream` sem `byte[]` ou arquivo temporário da aplicação.
4. Definir códigos estáveis de erro e o mapeamento mínimo de `ProblemDetail`.
5. Confirmar o ponto de publicação da mensagem e a ordem staging → publicação → resposta.

**Saída da fase:** contratos, dependências, configurações, limites arquiteturais e
semântica de falha aprovados; nenhuma implementação começa antes desses gates.

### Fase 2 — Lógica principal

1. Criar `DocumentContent` e o DTO multipart, mantendo o domínio/aplicação livres de
   `MultipartFile`.
2. Implementar a validação de parte `file`, `filename`, `Content-Type`, conteúdo vazio,
   assinatura Tika e limite de `52428800` bytes.
3. Implementar a gravação no bucket `docflow-staging`, contando bytes durante a leitura e
   fechando o stream no componente que o consome.
4. Atualizar a orquestração do Application Service para persistir os metadados no fluxo
   existente, gravar o staging e publicar somente o `contentReference`.
5. Garantir que falha na validação ou no staging não publique mensagem e não retorne
   aceitação assíncrona; a resposta só retorna após staging e publicação concluídos.

**Saída da fase:** fluxo interno completo e provider-neutral, com staging durável e sem
binário na mensagem.

### Fase 3 — Interface

1. Alterar `DocumentController` para `multipart/form-data` com a parte `file` obrigatória.
2. Remover o caminho JSON somente com metadados do `POST /documents` e documentar a
   quebra de contrato.
3. Integrar o publisher já definido pela feature de mensageria sem fazer o controller
   acessar RabbitMQ, MinIO ou seus SDKs.
4. Configurar o limite de upload e o bucket `docflow-staging` por ambiente, usando
   variáveis de configuração e sem hardcode de credenciais.
5. Mapear todos os erros de validação para `application/problem+json` com `ProblemDetail`.

**Saída da fase:** endpoint público multipart executável e integrado às bordas corretas,
sem estrutura paralela ao domínio `document`.

### Fase 4 — Testes

1. Testar DTO/controller: caminho feliz multipart, parte `file` ausente, JSON antigo,
   headers ausentes e resposta `ProblemDetail`.
2. Testar Tika: assinatura compatível, divergente e não identificada; testar conteúdo
   vazio e limite de `52428800` bytes.
3. Testar `CountingInputStream`, preservação do stream via `TikaInputStream` e fechamento
   após a gravação.
4. Testar o Application Service com portas falsas: ordem staging/publicação, referência
   correta, tamanho contado e ausência de publicação quando staging falha.
5. Testar o adaptador MinIO com bucket `docflow-staging` separado e mapeamento de falhas.
6. Executar integração do publisher/contrato RabbitMQ conforme a feature de mensageria e
   executar a regressão do backend existente, incluindo persistência, consulta e estados.

### Fase 5 — Entrega (deploy/rollback)

1. Atualizar documentação de configuração, criação/validação do bucket de staging,
   mensagens e limites de upload, sem incluir segredos.
2. Revisar o procedimento de rollback da quebra de contrato e o tratamento de objetos ou
   mensagens deixados por uma falha entre PostgreSQL, MinIO e RabbitMQ.
3. Confrontar `Spec da Feature ↔ Plano/Tarefas ↔ Código ↔ Testes` e registrar divergências.
4. Aguardar a revisão e aprovação de Marcos antes de qualquer deploy.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| O contrato público JSON será substituído por multipart obrigatório. | Alta | Alto: clientes e testes atuais podem falhar imediatamente. | Testar `400` para JSON antigo, documentar breaking change e preparar rollback operacional. | Sim, pela política de compatibilidade/versionamento da API. |
| Produto e arquitetura ainda dizem que binário, MinIO e RabbitMQ estão fora da fase atual. | Alta | Alto: a implementação pode divergir dos documentos aprovados. | Atualizar os documentos e registrar ADR antes do código, se a mudança de fase for aceita. | Sim. |
| A porta atual `DocumentStorage` não possui operação/bucket específico de staging. | Alta | Alto: estender o contrato pode afetar `MinioDocumentStorage` e a feature de mensageria. | Decidir entre uma porta de staging específica no limite existente ou extensão explícita de `DocumentStorage`; testar todos os adaptadores. | Sim, pois altera um limite arquitetural. |
| PostgreSQL, MinIO e RabbitMQ não têm transação distribuída. | Alta | Alto: falha após staging pode deixar objeto órfão ou publicação ausente. | Fechar a ordem das operações, publisher confirm, observabilidade e rollback sem assumir atomicidade. | Sim se for introduzida compensação, outbox ou novo estado persistido. |
| Tika pode rejeitar formatos legítimos que não tenham assinatura conhecida. | Média | Médio/alto: falsos negativos no recebimento. | Cobrir formatos suportados, registrar erro estável e manter a rejeição definida pela Spec; avaliar evolução somente em nova revisão. | Não inicialmente. |
| Validação/sniffing e gravação usam stream não reposicionável. | Média | Alto: consumir os primeiros bytes pode corromper o staging ou gerar tamanho incorreto. | Usar `TikaInputStream.get(stream)` e `CountingInputStream` na leitura efetiva; testar streams sem `mark/reset`. | Não, salvo mudança do contrato de conteúdo. |
| A versão real do projeto é Spring Boot `4.1.0`, enquanto a Spec menciona suporte de Spring Boot 3. | Alta | Médio: APIs, dependências ou testes de `ProblemDetail` podem divergir. | Confirmar se a regra é apenas o formato RFC 7807 e ajustar a redação/implementação à versão real antes do código. | Não, a menos que implique downgrade/upgrade deliberado. |

## 7. Perguntas abertas antes de começar

- A mudança de binário/RabbitMQ/MinIO deve atualizar formalmente a Spec do Produto, a
  Arquitetura e os ADRs atuais antes da implementação?
- A operação de staging será uma nova porta específica dentro do limite de storage ou
  uma extensão de `DocumentStorage`? A decisão deve preservar o acesso provider-neutral da
  mensageria ao `contentReference`.
- Qual é a ordem aprovada para persistir o documento, gravar staging e publicar a
  mensagem, e qual é o tratamento de objeto órfão em cada falha?
- A feature de mensageria já terá uma porta de publicação disponível quando esta feature
  for implementada, ou a integração deverá ser entregue em conjunto?
- O texto da Spec que menciona Spring Boot 3 deve ser ajustado para a versão real
  `4.1.0`, mantendo `ProblemDetail` e `application/problem+json` como contrato?
- Como o bucket `docflow-staging` será criado/validado no ambiente local sem alterar
  credenciais nem depender de criação manual não documentada?
