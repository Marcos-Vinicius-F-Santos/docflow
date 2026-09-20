# Arquitetura — DocFlow

Status: Atualizada — Feature backend operacional aprovada  
Última atualização: 2026-09-19

## 1. Visão geral do sistema

Esta é a arquitetura aplicada incrementalmente por feature. A feature aprovada de
registro binário recebe conteúdo, grava-o em staging no MinIO, publica uma referência
no RabbitMQ, entrega o consumidor que grava no storage final e reconcilia resultados
`RESULT_UNKNOWN` por scheduler interno. Frontend, autenticação e multi-tenant continuam
fora do escopo.

```text
[Cliente REST / Angular futuro]
            |
            v
     [Document API]
            |
            v
 [Document Application Service]
       |             |
       |             +--------------------> [PostgreSQL]
       |
       +-------------------------------> [RabbitMQ]
                                           |
                                           v
                              [Consumidor assíncrono]
                                           |
                                           v
                                [DocumentStorage port]
                                           |
                                           v
                              [MinIO adapter] -> [MinIO]
```

Essa árvore descreve os limites lógicos. As classes existentes que ainda permanecem
diretamente em `document/` (`Document`, `DocumentRepository` e `DocumentService`) não
serão relocadas nesta feature; os componentes novos de reconciliação continuam em
`document/application/`, e os adaptadores externos permanecem em `document/adapter/` e
`document/messaging/`, preservando o limite sem uma reorganização estrutural gratuita.

Fluxo de responsabilidades:

- a API REST recebe comandos e consultas e não acessa diretamente PostgreSQL, RabbitMQ
  ou MinIO;
- o serviço de aplicação coordena o caso de uso e os limites transacionais do banco;
- o domínio `Document` controla invariantes e transições de estado;
- RabbitMQ transporta os fluxos assíncronos desta fase;
- consumidores assíncronos dependem de portas da aplicação, não do SDK do MinIO;
- PostgreSQL persiste metadados, estado e referências do armazenamento;
- a entrada assíncrona usa a porta de staging para gravar o conteúdo no bucket
  `docflow-staging` antes da publicação, sem expor o SDK MinIO à aplicação;
- o adaptador MinIO traduz falhas do SDK para falhas compreensíveis pelo domínio.

Nesta fase, o endpoint de registro recebe a parte `file`, grava o conteúdo em staging
durável no MinIO e publica uma mensagem contendo somente `contentReference`. O consumidor
da feature lê o staging por uma porta própria e grava no storage final pela porta
`DocumentStorage`. Quando o resultado do storage é inconclusivo, o documento permanece
`PROCESSING` e o scheduler consulta `exists(objectKey)` sem repetir `store`.

RabbitMQ participa do fluxo de registro binário conforme a Spec da feature consolidada.
Sua topologia, contrato v1, retries `5s/15s/60s`, DLX/DLQ, publisher confirm e política
de consumidor `1/prefetch=1/concorrência 1..1` são definidos em
`specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md` e
implementados dentro de `document/messaging/rabbitmq/`. Outras features devem definir
seus próprios contratos antes de criar mensageria.

## 2. Princípio de organização: por domínio/feature, não por tipo de arquivo

O backend será organizado pelo domínio `document`, com responsabilidades internas
separadas por domínio, aplicação, portas e adaptadores. A organização-alvo é:

```text
backend/src/main/java/com/dockflow/dockflow/
└── document/
    ├── domain/
    │   ├── Document.java
    │   └── DocumentStatus.java
    ├── application/
    │   ├── DocumentService.java
    │   ├── DocumentContent.java
    │   └── DocumentStorageRequestPublisher.java
    ├── port/
    │   └── out/
    │       └── storage/
    │           ├── DocumentStorage.java
    │           ├── DocumentStaging.java
    │           ├── DocumentStagingReader.java
    │           └── DocumentStagingRemover.java
    ├── adapter/
    │   ├── in/
    │   │   └── web/
    │   │       ├── DocumentController.java
    │   │       ├── dto/
    │   │       ├── mapper/
    │   │       └── exception/
    │   └── out/
    │       ├── persistence/
    │       │   └── DocumentRepository.java
    │       └── storage/
    │           └── minio/
    │               └── MinioDocumentStorage.java
    └── messaging/
        └── rabbitmq/
            ├── DocumentStorageRequestedMessage.java
            ├── DocumentStorageRabbitTopology.java
            # publishers e consumidores de cada feature
```

Recursos compartilhados só devem ser criados quando forem usados por duas ou mais
features. A criação de novos módulos ou subpastas deve esperar conteúdo suficiente para
justificá-la.

As migrações permanecem em:

```text
backend/src/main/resources/db/migration/
```

A estrutura de infraestrutura local permanece em `infra/`, o frontend futuro em
`frontend/`, a documentação em `docs/` e as especificações em `specs/`.

## 3. Decisões grandes já tomadas

- [ADR-001 — Separação entre metadados e conteúdo binário](DECISAO/ADR-001-separacao-metadados-conteudo-binario.md)
- [ADR-002 — RabbitMQ para fluxos assíncronos](DECISAO/ADR-002-rabbitmq-fluxos-assincronos.md)
- [ADR-003 — Organização por domínio, portas e adaptadores](DECISAO/ADR-003-organizacao-por-dominio-portas-adaptadores.md)
- [ADR-004 — Entrada binária com staging assíncrono](DECISAO/ADR-004-entrada-binaria-staging-assincrono.md)
- [ADR-005 — Modelo persistido e claim da reconciliação](DECISAO/ADR-005-modelo-reconciliacao-result-unknown.md)

O ADR-005 define a agenda persistida, o claim com `FOR UPDATE SKIP LOCKED`, o uso de
`object_key` determinístico e a separação entre a lease de processamento RabbitMQ e a
agenda de reconciliação.

## 4. Limites que não devem ser cruzados

- A camada de API não acessa diretamente o banco, RabbitMQ, MinIO ou o SDK do MinIO.
- A lógica de negócio não depende diretamente do SDK do MinIO; usa a porta
  `DocumentStorage`.
- O domínio controla as transições de `DocumentStatus` e não deve permitir alterações
  de estado fora dos métodos de negócio definidos.
- O serviço de aplicação coordena os casos de uso e os limites transacionais do
  PostgreSQL; o controller não contém regras de negócio.
- Adaptadores traduzem detalhes externos — JPA, RabbitMQ e SDK do MinIO — para
  contratos usados pela aplicação.
- Cada fluxo assíncrono deve ter mensagem, topologia e comportamento de retry definidos
  pela Spec da feature que o utiliza. Nesta feature consolidada, esses contratos já estão
  definidos; a implementação deve manter publisher e consumidor nas bordas RabbitMQ.
- PostgreSQL e MinIO não participam de uma transação distribuída. A arquitetura deve
  representar estados parciais explicitamente e nunca promover um resultado
  inconclusivo a sucesso.
- Alterações de schema devem ser feitas por migrations Flyway, com caminho de volta
  operacional; não alterar dados existentes diretamente como substituto de migration.
- Credenciais e segredos devem vir de configuração/ambiente e não podem ser
  hardcoded.

## 5. O que fica de fora (por agora)

- implementação do frontend Angular;
- limpeza automática de objetos órfãos após `FAILED`;
- topologia detalhada de exchanges, queues, retries e dead-letter de outras features sem
  Spec própria;
- contratos de mensagens de features que ainda não possuem spec própria;
- deploy e hospedagem externa;
- autenticação, autorização, multi-tenant e outros domínios não descritos na Spec de
  Produto.

Cada item que sair deste escopo deve primeiro receber uma spec de feature, critérios de
aceitação e, quando alterar limites ou contratos, uma decisão arquitetural aprovada.
