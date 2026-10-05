# Spec do Produto — DocFlow

Status: Atualizada — backend operacional da Feature aprovada  
Tipo de projeto: Portfólio  
Criado em: 2026-09-14  
Última atualização: 2026-09-19

## 1. Objetivo

O DocFlow é um backend para registrar e acompanhar documentos, demonstrando
habilidades de Software Engineering em um projeto de portfólio.

O sistema é voltado principalmente ao próprio autor e a outros desenvolvedores que
queiram avaliar uma implementação com API REST, persistência de metadados, máquina de
estados explícita, separação entre domínio e infraestrutura, migrações de banco e
testes automatizados, mantendo extensões futuras para conteúdo binário.

Nesta fase, o sistema trabalha com metadados e recebe conteúdo binário pelo registro,
gravando-o em staging no MinIO antes de publicar a solicitação assíncrona no RabbitMQ.
O consumidor grava o conteúdo no storage final, confirma o documento somente após a
operação correspondente e usa reconciliação para resultados `RESULT_UNKNOWN`.

## 2. Sistema existente (só se for brownfield)

O backend Java/Spring Boot já permite registrar documentos por REST, consultar um
documento por identificador e acompanhar seu ciclo de vida. Os metadados
`originalFilename`, `contentType` e `sizeBytes` são persistidos em PostgreSQL. O
backend também contém a abstração `DocumentStorage`, staging provider-neutral, o
adaptador MinIO e os adaptadores RabbitMQ para o fluxo binário operacional.

O domínio valida seus próprios invariantes e controla as transições entre os estados
`PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`. O backend possui testes unitários e de
integração; o frontend está apenas scaffoldado e ainda não foi implementado.

## 3. Funcionalidades (visão geral, em ordem de construção)

1. **Infraestrutura local e persistência inicial** — disponibilizar PostgreSQL e MinIO
   localmente com Docker Compose e gerenciar o schema por migrações Flyway.
2. **Registro de documentos** — expor `POST /documents` como `multipart/form-data` para
   registrar os metadados derivados da parte `file` e gravar o binário em staging antes
   da publicação assíncrona.
3. **Ciclo de vida do documento** — controlar as transições explícitas
   `PENDING → PROCESSING → COMPLETED`, ou `FAILED` quando ocorrer uma falha permanente.
   `RESULT_UNKNOWN` mantém `PROCESSING` até a reconciliação confirmar o objeto ou
   esgotar cinco tentativas.
4. **Consulta de documentos** — expor `GET /documents/{id}` para consultar um documento
   registrado e mapear seus dados para o DTO de resposta.
5. **Armazenamento final do conteúdo binário** — consumir a referência de staging e
   persistir o binário no storage final por meio de uma porta provider-neutral, sem
   acoplar a lógica de negócio ao SDK do MinIO.
6. **Confirmação segura do armazenamento** — somente tratar o documento como concluído
   quando a gravação no object storage for confirmada e sua referência estável estiver
   persistida. Falhas rejeitadas, indisponibilidade e resultados desconhecidos não
   não podem ser convertidos silenciosamente em sucesso. Falhas rejeitadas vão para
   `FAILED`/DLQ; resultados desconhecidos seguem retry e reconciliação.
7. **Frontend Angular** — implementar, em uma fase posterior, uma interface Angular
   para consumir o backend. O frontend ainda não faz parte da implementação atual.

## 4. Fora de escopo

Nesta fase, não fazem parte do produto:

- frontend implementado; o projeto Angular está apenas scaffoldado;
- deploy ou hospedagem em qualquer ambiente externo;
- uma transação distribuída entre PostgreSQL e MinIO;
- limpeza automática de objetos órfãos de staging ou storage final após `FAILED`;
- funcionalidades que não foram descritas nesta fase, como novos fluxos de negócio
   além do registro, acompanhamento de estado e consulta por identificador.

PostgreSQL e MinIO permanecem sistemas separados. A arquitetura assume que não existe
atomicidade distribuída entre eles e usa estado explícito e reconciliação para lidar
com essa realidade.

## 5. Stack técnico e por quê

| Camada | Escolha | Motivo |
|---|---|---|
| Frontend | Angular | Escolha definida para uma fase futura. O motivo original da escolha não foi documentado. |
| Backend | Java com Spring Boot — Web MVC, Data JPA, Validation, Actuator e Flyway | Escolha definida para o backend. O motivo original da escolha não foi documentado. |
| Banco de dados | PostgreSQL | Escolha definida para persistir metadados e estado dos documentos. O motivo original da escolha não foi documentado. |
| Armazenamento de objetos | MinIO, acessado pelas portas provider-neutral `DocumentStorage` e staging | Separa conteúdo, staging e metadados sem transação distribuída; credenciais root e da aplicação são distintas. |
| Mensageria | RabbitMQ | Transporta referências de staging com publisher confirm, retries, DLQ e política de concorrência explícita. |
| Infraestrutura local | Docker Compose, com PostgreSQL, RabbitMQ, MinIO e bootstrap | Reproduz os serviços e valida credenciais/buckets a partir do `.env.example`. |
| Testes | JUnit e Testcontainers para PostgreSQL, RabbitMQ e MinIO | Isola unitários e valida o fluxo operacional com dependências gerenciadas pela suíte. |
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
│       │       └── storage/
│       │           └── minio/
│       ├── main/resources/db/migration/
│       │   ├── V1__initial_schema.sql
│       │   ├── V2__create_documents_table.sql
│       │   ├── V3__add_document_storage_metadata.sql
│       │   ├── V4__add_reconciliation_attempts.sql
│       │   └── V5__schedule_result_unknown_reconciliation.sql
│       └── test/java/com/dockflow/dockflow/document/
├── frontend/                 # Angular, futuro
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
- **Serviço** — `DocumentService` orquestra registro e consulta usando limites
  transacionais do Spring.
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

## 7. Critérios de sucesso

O projeto será considerado funcionando nesta fase quando:

- um documento puder ser registrado pelo endpoint `POST /documents` com seus
  metadados (`originalFilename`, `contentType` e `sizeBytes`);
- os metadados do documento forem persistidos em PostgreSQL;
- o registro multipart grave o conteúdo em staging e publique a referência para o fluxo
  assíncrono definido;
- falhas conhecidas levem o documento a `FAILED` e as transições válidas da máquina
  de estados sejam respeitadas;
- transições inválidas sejam impedidas pelo domínio;
- um documento registrado possa ser consultado por `GET /documents/{id}`;
- as migrações Flyway criem/atualizem o schema esperado sem alteração manual direta
  do banco;
- o armazenamento final e a confirmação segura permaneçam separados do recebimento e
  staging desta feature;
- os testes unitários e de integração existentes cubram o fluxo principal e as falhas
  relevantes, sem regressão óbvia no backend;
- outra pessoa desenvolvedora consiga iniciar PostgreSQL e MinIO localmente com
  Docker Compose e executar o backend seguindo a documentação do projeto.

## 8. Quem usa IA e como

- [ ] Eu escrevo todo o código (ex: Aegis-Core)
- [x] IA implementa a partir da minha spec/arquitetura, eu reviso e aprovo (padrão)

Marcos é o aprovador final da Spec, da implementação, dos critérios de aceitação e de
qualquer deploy futuro.
