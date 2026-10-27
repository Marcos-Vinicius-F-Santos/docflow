# Spec do Produto — DocFlow

Status: Atualizada — backend operacional e frontend Angular validados localmente
Tipo de projeto: Portfólio  
Criado em: 2026-09-14  
Última atualização: 2026-10-27

## 1. Objetivo

O DocFlow é uma aplicação para registrar e acompanhar documentos, demonstrando
práticas de Software Engineering em um projeto de portfólio.

O sistema é voltado principalmente ao próprio autor e a outros desenvolvedores que
queiram avaliar uma implementação com API REST, interface Angular, persistência de
metadados, máquina de estados explícita, separação entre domínio e infraestrutura,
mensageria assíncrona, migrações de banco e testes automatizados.

O fluxo atual recebe conteúdo binário por `multipart/form-data`, grava-o em staging no
MinIO antes de publicar a solicitação no RabbitMQ, processa o conteúdo no storage final
e confirma o documento somente após a operação correspondente. Resultados
`RESULT_UNKNOWN` permanecem em processamento até a reconciliação confirmar ou esgotar
as tentativas previstas.

## 2. Sistema existente (só se for brownfield)

O backend Java/Spring Boot permite registrar documentos por `POST /documents`, consultar
um documento por `GET /documents/{documentId}`, listar documentos por `GET /documents`,
baixar o conteúdo final por `GET /documents/{documentId}/content` e excluir
definitivamente um documento por `DELETE /documents/{documentId}`.

Os metadados `originalFilename`, `contentType`, `sizeBytes`, estado e timestamps são
persistidos em PostgreSQL. O conteúdo binário é separado dos metadados: o recebimento
usa staging no MinIO, o processamento assíncrono usa RabbitMQ e o storage final é
acessado por portas provider-neutral.

O domínio controla as transições entre `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.
O backend também possui retries, DLQ, publisher confirms, concorrência explícita,
timeouts operacionais, scheduler de reconciliação de `RESULT_UNKNOWN` e testes
unitários e de integração.

O frontend Angular está funcional para o fluxo atual. Ele oferece upload com validação
local e progresso, acompanhamento por identificador com polling, listagem, download
individual e exclusão individual com confirmação explícita. O frontend consome apenas a
API REST e não acessa diretamente PostgreSQL, RabbitMQ ou MinIO.

## 3. Funcionalidades (visão geral, em ordem de construção)

1. **Infraestrutura local e persistência** — disponibilizar PostgreSQL, RabbitMQ e
   MinIO por Docker Compose, com bootstrap idempotente e schema gerenciado por
   migrações Flyway.
2. **Registro de documentos** — expor `POST /documents` como `multipart/form-data` para
   registrar os metadados derivados da parte `file` e gravar o binário em staging antes
   da publicação assíncrona.
3. **Ciclo de vida do documento** — controlar as transições explícitas
   `PENDING → PROCESSING → COMPLETED`, ou `FAILED` quando ocorrer uma falha permanente.
   `RESULT_UNKNOWN` mantém `PROCESSING` até a reconciliação confirmar o objeto ou
   esgotar cinco tentativas.
4. **Consulta de documentos** — consultar um documento por identificador e listar os
   documentos persistidos nos quatro estados públicos.
5. **Armazenamento final do conteúdo binário** — consumir a referência de staging e
   persistir o binário no storage final por meio de uma porta provider-neutral, sem
   acoplar a lógica de negócio ao SDK do MinIO.
6. **Confirmação segura do armazenamento** — somente tratar o documento como concluído
   quando a gravação no object storage for confirmada e sua referência estável estiver
   persistida. Falhas rejeitadas, indisponibilidade e resultados desconhecidos não
   podem ser convertidos silenciosamente em sucesso. Falhas rejeitadas vão para
   `FAILED`/DLQ; resultados desconhecidos seguem retry e reconciliação.
7. **Download individual** — transmitir o conteúdo de um documento `COMPLETED` em
   streaming, preservando o tipo, o tamanho e o nome original.
8. **Exclusão definitiva individual** — exigir confirmação explícita no frontend e
   remover o registro, o objeto final e o staging conforme a política do ADR-006.
9. **Frontend Angular** — permitir selecionar e enviar um arquivo, acompanhar seu
   processamento, consultar a listagem, baixar documentos concluídos e excluir
   documentos individualmente.
10. **Operação e verificação** — manter documentação de execução local, contratos HTTP,
    migrations, testes e procedimentos de rollback.

## 4. Fora de escopo

Nesta versão, não fazem parte do produto:

- busca por filename, filtros, paginação e ordenação avançada da listagem;
- download em lote e exclusão em lote;
- preview, visualização inline, suporte a `Range` ou retomada de download;
- autenticação, autorização, multi-tenant, CORS ou BFF;
- deploy ou hospedagem em qualquer ambiente externo;
- uma transação distribuída entre PostgreSQL, RabbitMQ e MinIO;
- limpeza automática genérica de objetos órfãos após `FAILED`;
- reprocessamento automático iniciado pelo usuário.

Busca, filtros, paginação, ordenação e operações em lote possuem uma feature aprovada
e planos próprios, mas permanecem como evolução posterior ao baseline atual.

PostgreSQL, RabbitMQ e MinIO permanecem sistemas separados. A arquitetura assume que
não existe atomicidade distribuída entre eles e usa estados explícitos, idempotência e
reconciliação para lidar com essa realidade.

## 5. Stack técnico e por quê

| Camada | Escolha | Motivo |
|---|---|---|
| Frontend | Angular | Interface local para upload, acompanhamento e operações individuais, consumindo a API REST sem acesso direto à infraestrutura. |
| Backend | Java com Spring Boot — Web MVC, Data JPA, Validation, Actuator e Flyway | Implementar a API REST, as regras de domínio, persistência, validação e migrações. |
| Banco de dados | PostgreSQL | Persistir metadados, estado, referências de storage e agenda de reconciliação. |
| Armazenamento de objetos | MinIO, acessado pelas portas provider-neutral `DocumentStorage` e staging | Separa conteúdo, staging e metadados sem transação distribuída; credenciais root e da aplicação são distintas. |
| Mensageria | RabbitMQ | Transporta referências de staging com publisher confirm, retries, DLQ e política de concorrência explícita. |
| Infraestrutura local | Docker Compose, com PostgreSQL, RabbitMQ, MinIO e bootstrap | Reproduz os serviços e valida credenciais/buckets a partir do `.env.example`. |
| Testes | JUnit e Testcontainers no backend; testes Angular no frontend | Isolar regras e validar o fluxo operacional com dependências gerenciadas pela suíte. |
| Deploy/Hospedagem | Nenhum nesta fase | Não existe deploy ou hospedagem planejada para a fase atual. |

As credenciais de PostgreSQL e MinIO devem ser fornecidas por variáveis/configuração de
ambiente, seguindo `infra/.env.example`; segredos não fazem parte do código versionado.

## 6. Arquitetura de pastas

A organização segue o domínio/feature de documentos, conforme os princípios do projeto.
O resumo abaixo representa a estrutura atual e sua evolução planejada; a especificação
detalhada está em `specs/02-arquitetura/ARQUITETURA.md`.

```text
docflow/
├── backend/
│   └── src/
│       ├── main/java/com/dockflow/dockflow/
│       │   └── document/
│       │       ├── Document.java
│       │       ├── DocumentStatus.java
│       │       ├── DocumentRepository.java
│       │       ├── DocumentService.java
│       │       ├── controller/
│       │       ├── dto/
│       │       ├── exception/
│       │       ├── mapper/
│       │       ├── application/
│       │       ├── adapter/
│       │       ├── messaging/
│       │       ├── port/
│       │       └── storage/
│       │           └── minio/
│       ├── main/resources/db/migration/
│       │   ├── V1__initial_schema.sql
│       │   ├── V2__create_documents_table.sql
│       │   ├── V3__add_document_storage_metadata.sql
│       │   ├── V4__add_reconciliation_attempts.sql
│       │   └── V5__schedule_result_unknown_reconciliation.sql
│       └── test/java/com/dockflow/dockflow/document/
├── frontend/                 # Aplicação Angular do fluxo de documentos
├── infra/
│   ├── docker-compose.yml
│   ├── bootstrap/
│   ├── .env.example
│   └── .env
├── docs/
└── specs/
```

Responsabilidades principais:

- **Domínio** — `Document` e `DocumentStatus` impõem invariantes e expõem somente
  transições válidas (`startProcessing`, `markCompleted`, `markFailed`).
- **Aplicação** — coordena registro, publicação, download, exclusão e reconciliação
  dentro dos limites transacionais definidos.
- **API** — `DocumentController` implementa a camada REST e delega ao serviço,
  convertendo entre requisições/respostas e o domínio por DTOs.
- **Armazenamento** — `DocumentStorage` é a porta voltada ao domínio; `MinioDocumentStorage`
  traduz falhas específicas do SDK para falhas de negócio
  (`UNAVAILABLE`, `REJECTED`, `RESULT_UNKNOWN`).
- **Mensageria** — RabbitMQ transporta o contrato v1 do armazenamento assíncrono, com
  publisher confirm, consumidor manual, retries `5s/15s/60s`, DLQ e concorrência `1..1`.
- **Reconciliação** — o scheduler interno consulta documentos `PROCESSING` elegíveis,
  usa claim atômico no PostgreSQL e verifica `exists(objectKey)` nos atrasos aprovados.
- **Persistência** — Spring Data JPA e PostgreSQL persistem metadados, estado e
  referências do armazenamento; Flyway gerencia as alterações de schema.
- **Frontend** — Angular concentra interação, estados visuais, polling, confirmação e
  download; não acessa serviços de infraestrutura diretamente.

## 7. Critérios de sucesso

O produto será considerado funcionando nesta versão quando:

- um documento puder ser enviado pelo frontend ou registrado por `POST /documents` com
  `multipart/form-data`;
- o backend validar o conteúdo, persistir os metadados e gravar o binário em staging;
- a referência de staging puder ser publicada e processada pelo fluxo RabbitMQ;
- documentos puderem terminar em `COMPLETED` ou `FAILED` sem violar as transições de
  domínio;
- `RESULT_UNKNOWN` não for convertido silenciosamente em sucesso e puder ser tratado
  pelo scheduler de reconciliação;
- documentos puderem ser consultados por identificador e listados em qualquer estado
  público;
- conteúdos `COMPLETED` puderem ser baixados individualmente em streaming;
- a exclusão individual exigir confirmação no frontend e remover os recursos associados
  conforme o ADR-006;
- as migrações Flyway criem/atualizem o schema esperado sem alteração manual direta
  do banco;
- os testes prioritários do backend e frontend cubram o caminho principal e falhas
  relevantes, sem regressão óbvia;
- outra pessoa desenvolvedora consiga iniciar a infraestrutura local e executar o
  backend e o frontend seguindo a documentação do projeto.

## 8. Quem usa IA e como

- [ ] Eu escrevo todo o código (ex: Aegis-Core)
- [x] IA implementa a partir da minha spec/arquitetura, eu reviso e aprovo (padrão)

Marcos é o aprovador final da Spec, da implementação, dos critérios de aceitação e de
qualquer deploy futuro.
