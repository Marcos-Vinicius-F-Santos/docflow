# DocFlow — casos de uso, serviços e decisões

Status: documentação consolidada do worktree atual  
Data da verificação: 2026-10-29  
Escopo: backend Spring Boot, frontend Angular, infraestrutura local e documentação de arquitetura

Este documento é um índice operacional e arquitetural. As specs, os planos, as ADRs e os
documentos de operação continuam sendo as fontes detalhadas de cada decisão. O objetivo
é explicar como as partes se combinam em um caso de uso completo e apontar onde cada
responsabilidade está implementada.

## 1. Visão do sistema

O DocFlow registra documentos, guarda seus metadados no PostgreSQL e processa o conteúdo
binário de forma assíncrona. O frontend Angular conversa somente com a API REST e com o
WebSocket de notificações; não acessa banco, filas ou object storage diretamente.

```text
Usuário
  |
  v
Angular Hub -----------------------> GET /ws/notifications
  |                                  (eventos de ciclo de vida)
  |
  +-------------------------------> API REST Spring Boot
                                      |
                                      +--> PostgreSQL
                                      |    metadados, estado e logs
                                      |
                                      +--> MinIO staging
                                      |    conteúdo recebido
                                      |
                                      +--> RabbitMQ
                                      |    armazenamento, exclusão,
                                      |    notificações e logs
                                      |
                                      +--> MinIO final
                                      |    conteúdo confirmado
                                      |
                                      +--> Web3Forms opcional
                                           e-mail assíncrono
```

O “serviço de logs” e o “serviço de notificações” são componentes lógicos do mesmo
backend nesta versão. Não existem microserviços implantáveis separados.

## 2. Caso de uso principal: registrar e processar um documento

### Atores e pré-condições

- Usuário usando o Hub ou cliente HTTP autorizado pelo ambiente local.
- PostgreSQL, RabbitMQ e MinIO disponíveis conforme [operação local](document-registration-operations.md).
- O arquivo é enviado como uma única parte `file` de `multipart/form-data`.
- O limite atual de entrada é 50 MiB (`52.428.800` bytes).

### Fluxo feliz

1. O usuário seleciona um arquivo ou usa arrastar e soltar no Hub.
2. O Angular valida presença, nome, tamanho e tipo/conteúdo localmente.
3. `DocumentApiService` envia `POST /documents` com somente a parte `file` e acompanha o progresso.
4. `DocumentController` rejeita partes extras, transforma a entrada em `DocumentContent` e não deixa
   `MultipartFile` atravessar o limite do adaptador HTTP.
5. `DocumentContentValidator` verifica conteúdo não vazio, tamanho real e compatibilidade entre o
   tipo declarado e a assinatura detectada pelo Apache Tika.
6. `DocumentService` cria o identificador do documento e grava o stream no bucket de staging do MinIO
   em `staging/{documentId}`.
7. O backend persiste os metadados com estado `PENDING` e confirma o flush no PostgreSQL.
8. O publisher publica no RabbitMQ a mensagem `DocumentStorageRequested` v1, contendo metadados e a
   referência durável do staging, nunca o binário do request.
9. O consumidor valida a mensagem, obtém o claim/lease do documento e verifica a idempotência do
   objeto final.
10. O consumidor lê o staging, grava `documents/{documentId}` no storage final e só remove o staging
    após a confirmação da gravação.
11. O domínio promove o documento para `COMPLETED`, persiste a referência final e emite o evento de
    ciclo de vida correspondente.
12. O Hub recebe a notificação, atualiza a lista e permite acompanhar, baixar ou visualizar o documento.

### Estados e falhas

```text
PENDING --> PROCESSING --> COMPLETED
   |           |
   |           +--> RESULT_UNKNOWN: permanece PROCESSING e entra em reconciliação
   |
   +-------------------------------> FAILED em falha permanente
```

- Falha de validação retorna `application/problem+json` e não inicia o processamento.
- Falha de publicação, storage ou broker não é convertida silenciosamente em sucesso.
- Falhas transitórias do armazenamento usam retries de `5s`, `15s` e `60s`, além da entrega inicial,
  com DLQ após o limite.
- `RESULT_UNKNOWN` não repete `store`: o scheduler consulta `exists(objectKey)` nas janelas de
  reconciliação e confirma ou esgota as tentativas.
- O processamento usa consumidor único, `prefetch=1` e concorrência `1..1` para manter o claim e a
  idempotência explícitos.

Implementação principal: [DocumentController.java](../backend/src/main/java/com/dockflow/dockflow/document/controller/DocumentController.java),
[DocumentService.java](../backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java) e
[DocumentStorageMessageConsumer.java](../backend/src/main/java/com/dockflow/dockflow/document/messaging/rabbitmq/DocumentStorageMessageConsumer.java).

## 3. Casos de uso públicos

### UC-01 — Consultar documentos

- `GET /documents` lista os documentos persistidos nos estados públicos.
- `GET /documents/{documentId}` retorna metadados, estado, timestamps, contador de falhas e dados de
  exclusão quando aplicáveis.
- O Hub mostra a lista completa, os cinco documentos mais recentes e até cinco documentos com falha.
- Após reconexão do WebSocket, o Hub faz uma consulta REST para recuperar o estado atual.

### UC-02 — Acompanhar processamento

- A rota `/documents/{documentId}` mostra identificador, nome, tipo, tamanho, datas e uma linha do
  tempo de estados.
- Enquanto o estado é `PENDING` ou `PROCESSING`, o frontend consulta o backend automaticamente a cada
  três segundos e também oferece atualização manual.
- A tela não acessa RabbitMQ, PostgreSQL ou MinIO diretamente.

### UC-03 — Baixar ou visualizar conteúdo

- `GET /documents/{documentId}/content` faz streaming do conteúdo `COMPLETED` com nome, tipo e tamanho
  originais.
- `GET /documents/{documentId}/preview` usa `Content-Disposition: inline`.
- O preview é oferecido pelo frontend somente para PDFs concluídos.
- Conteúdo pendente, em processamento ou falho retorna indisponibilidade; o backend não devolve
  credenciais, bucket ou `objectKey` ao cliente.

### UC-04 — Reprocessar manualmente um documento falho

- O usuário pode acionar `POST /documents/{documentId}/retry` quando o documento está `FAILED`, ainda
  possui tentativas disponíveis e não está marcado para exclusão.
- O retry reutiliza `staging/{documentId}`; não exige novo upload.
- A falha inicial conta como a primeira falha. O limite é de três falhas; não existe quarta tentativa.
- O retry aceito retorna `202 Accepted` e o documento volta para `PROCESSING`.

### UC-05 — Excluir definitivamente um documento

- O frontend exige confirmação explícita antes de chamar `DELETE /documents/{documentId}`.
- O backend remove o objeto final quando existir, remove o staging determinístico e só então remove o
  registro do PostgreSQL.
- Falha em qualquer remoção mantém o documento disponível para nova tentativa e não é reportada como
  sucesso.
- O fluxo de exclusão atrasada após três falhas usa RabbitMQ; a exclusão manual continua disponível.

### UC-06 — Receber notificações de ciclo de vida

- O endpoint WebSocket é `/ws/notifications`.
- Os eventos públicos são `DOCUMENT_PROCESSING_STARTED`, `DOCUMENT_COMPLETED`, `DOCUMENT_FAILED`,
  `DOCUMENT_DELETION_WARNING` e `DOCUMENT_DELETED`.
- O Hub mostra toast e banner de sessão, atualiza documentos existentes e remove documentos excluídos.
- O cliente reconecta com backoff e deduplica eventos por `eventId`.
- WebSocket, RabbitMQ e e-mail são canais desacoplados: falha de notificação não altera estado,
  contador, retry ou exclusão do documento.
- O e-mail é opcional e usa Web3Forms atrás de uma porta provider-neutral, com credenciais apenas no
  backend.

Detalhes: [document-notifications.md](document-notifications.md) e
[ADR-008](../specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md).

### UC-07 — Consultar logs operacionais

- `GET /logs` é somente leitura, paginado e ordenado por `occurredAt` decrescente e `eventId` como
  desempate.
- Filtros disponíveis: `level`, `service`, `component`, `operation`, `documentId`, `correlationId`,
  `occurredFrom` e `occurredTo`.
- A rota Angular `/logs` permite aplicar e limpar filtros, navegar entre páginas e abrir o documento
  associado quando o `documentId` é válido.
- Os níveis públicos são `INFO`, `WARN` e `ERROR`.
- Logs persistidos têm retenção de três horas. A consulta não edita, exclui nem reprocessa logs.
- O serviço de logs não pode alterar documentos nem transformar falha de logging em falha do negócio.

Detalhes: [contrato HTTP](api.md#logs), [logs operacionais](../specs/03-features/logs-operacionais/spec.md)
e [ADR-009](../specs/02-arquitetura/DECISAO/ADR-009-mensageria-para-logs-operacionais.md).

## 4. Catálogo de serviços e responsabilidades

### Backend

| Serviço/componente | Responsabilidade |
|---|---|
| `DocumentController` | Entrada HTTP de upload, consulta, retry, download, preview e exclusão. |
| `DocumentService` | Casos de uso de documento, transações, staging, publicação, retry, download e exclusão. |
| `Document` | Máquina de estados e invariantes do domínio. |
| `DocumentContentValidator` | Validação de stream, tipo detectado, tipo declarado e limite. |
| `DocumentReconciliationService` | Reconciliação de `RESULT_UNKNOWN` via `exists(objectKey)`. |
| `DocumentReconciliationScheduler` | Seleção periódica de documentos elegíveis e disparo da reconciliação. |
| `DocumentStorageMessageConsumer` | Consumo do armazenamento, claim, idempotência, retry, DLQ e limpeza do staging. |
| `RabbitMqDocumentStorageRequestPublisher` | Publicação confirmada da solicitação de armazenamento. |
| `DocumentDeletionMessageConsumer` | Exclusão atrasada e remoção coordenada do conteúdo e metadados. |
| `DocumentLifecycleEventPublisher` | Publicação dos eventos de ciclo de vida. |
| `DocumentNotificationMessageConsumer` | Entrega dos eventos para os canais de notificação. |
| `DocumentNotificationWebSocketHandler` | Broadcast dos eventos para as sessões do Hub. |
| `DocumentEmailMessageConsumer` | Retry, DLQ e entrega assíncrona de e-mail. |
| `Web3FormsEmailSender` | Adaptador externo de e-mail; não contém regras do documento. |
| `ApplicationLogRecorder` | Sanitização, escrita local estruturada e publicação assíncrona de logs. |
| `ApplicationLogMessageConsumer` | Validação, idempotência e persistência de eventos de log. |
| `ApplicationLogQueryService` | Validação dos filtros e consulta paginada read-only. |
| `ApplicationLogRetentionService` | Remoção agendada de logs com mais de três horas. |
| `GlobalExceptionHandler` | Conversão de erros de domínio e infraestrutura para respostas HTTP seguras. |

### Adaptadores e infraestrutura

| Serviço | Uso |
|---|---|
| PostgreSQL | Metadados de documentos, estados, agenda de reconciliação e logs persistidos. |
| MinIO | Bucket de staging e bucket final do conteúdo binário. |
| RabbitMQ | Armazenamento assíncrono, exclusão atrasada, notificações, e-mail e logs. |
| Web3Forms | Provedor externo opcional de e-mail, configurado por variáveis de ambiente. |
| Docker Compose/bootstrap | Inicialização idempotente, validação de dependências e criação dos buckets. |
| Flyway | Evolução expand-only do schema por migrations. |

### Frontend

| Serviço/componente | Responsabilidade |
|---|---|
| `HubPage` | Upload, validação, progresso, lista, resumo, notificações e estados de erro. |
| `DocumentDetailPage` | Acompanhamento, polling, download, retry, preview e exclusão confirmada. |
| `DocumentApiService` | Cliente REST de documentos e construção das URLs de conteúdo/preview. |
| `DocumentStatusPollerService` | Observação periódica do documento até estado terminal. |
| `DocumentStatusPanel` | Apresentação da máquina de estados. |
| `DocumentNotificationService` | Conexão, reconexão e parsing do WebSocket. |
| `NotificationSessionState` | Validação e deduplicação de eventos por sessão. |
| `LogsPage` | Consulta read-only, filtros, paginação e navegação para documentos. |
| `LogsApiService` | Cliente REST de `GET /logs`. |

## 5. Decisões arquiteturais consolidadas

| ADR | Decisão | Consequência prática |
|---|---|---|
| ADR-001 | Separar metadados/estado no PostgreSQL do binário no MinIO. | Não há transação distribuída; estados e reconciliação tornam resultados parciais tratáveis. |
| ADR-002 | Usar RabbitMQ para fluxos assíncronos. | HTTP não fica acoplado ao storage; publisher confirm, retry e DLQ são explícitos. |
| ADR-003 | Organizar por domínio, portas e adaptadores. | Regras de documento não dependem de SDKs, HTTP ou provedores externos. |
| ADR-004 | Gravar staging durável antes de publicar a mensagem. | O consumidor recebe uma referência reutilizável, não um stream HTTP expirado. |
| ADR-005 | Persistir agenda, tentativas e claim da reconciliação. | Dois schedulers não processam o mesmo documento; reinício não perde o trabalho. |
| ADR-006 | Fazer exclusão definitiva coordenada e recuperável em falha. | Objetos e metadados são removidos em ordem segura; não há exclusão lógica. |
| ADR-007 | Fazer retry manual pelo staging e exclusão atrasada após três falhas. | O usuário não precisa reenviar o arquivo; o limite de tentativas é determinístico. |
| ADR-008 | Separar eventos de ciclo de vida dos canais WebSocket e e-mail. | Falha de entrega não muda o documento; Web3Forms é substituível atrás de uma porta. |
| ADR-009 | Usar logs locais como fallback e RabbitMQ/PostgreSQL para consulta assíncrona. | Há diagnóstico local durante falha do broker, persistência idempotente e retenção controlada. |

Índice completo: [decisões arquiteturais](../specs/02-arquitetura/DECISAO/).

## 6. Limites e funcionalidades não implementadas

Não fazem parte do comportamento atual verificado:

- autenticação, autorização, usuários e multi-tenant;
- busca por nome, filtros, paginação e ordenação da listagem de documentos;
- download em lote e exclusão em lote;
- preview de formatos diferentes de PDF, `Range` e retomada de download;
- edição, exclusão manual ou reprocessamento pela tela de logs;
- histórico persistido de notificações ou replay de eventos WebSocket;
- outbox ou transação distribuída entre PostgreSQL, RabbitMQ e MinIO;
- deploy/hospedagem externa.

As specs aprovadas de evolução não devem ser tratadas como funcionalidade já disponível até
que código, testes e critérios de aceite estejam convergentes.

## 7. Verificação executada nesta rodada

| Verificação | Resultado | Observação |
|---|---|---|
| `npm.cmd run build` em `frontend/` | Passou | Bundle Angular gerado; aviso não bloqueante: `hub-page.scss` excede o orçamento de 4 KiB em 1,87 KiB. |
| `npm.cmd test -- --watch=false` em `frontend/` | Não executou testes | O runner falhou antes da coleta das 12 suítes ao resolver `@angular/core` no `init-testbed.js` temporário. |
| `./mvnw.cmd test` em `backend/` | Inconclusivo | A execução ficou sem saída e foi interrompida após mais de um minuto, sem resumo final. Há testes Testcontainers e integrações reais no conjunto. |
| `git diff --check` | Sem erro de whitespace | Foram emitidos apenas avisos de normalização LF/CRLF em arquivos já modificados. |
| Smoke test HTTP/infraestrutura | Não executado | Não foi possível declarar a execução completa do backend e das dependências após o bloqueio da suíte Maven. |

Portanto, esta rodada confirma o build do frontend e a existência dos componentes documentados,
mas não deve ser reportada como aprovação final do backend ou como gate de deploy. A aprovação
final continua pendente de Marcos, conforme `AGENTS.md`.

## 8. Divergências e pontos de revisão

1. A implementação atual, a spec de notificações, o ADR-008 e a configuração usam Web3Forms.
   Alguns trechos históricos de `specs/04-plano/notificacoes-documentos/` ainda mencionam Brevo.
   Esses trechos precisam ser tratados como histórico da implementação substituída, não como
   configuração operacional atual.
2. A spec de logs mantém o Definition of Done não marcado, embora o worktree contenha código,
   testes e documentação da feature. É necessário registrar a revisão/aprovação de Marcos antes
   de considerar a feature concluída.
3. O worktree contém mudanças não commitadas em backend, frontend, specs e documentação. Este
   documento descreve esse estado de trabalho; não representa sozinho um commit ou uma versão
   aprovada para deploy.

## 9. Referências operacionais

- [Contrato HTTP](api.md)
- [Arquitetura](architecture.md)
- [Banco e migrations](database.md)
- [Operação local](document-registration-operations.md)
- [Frontend](frontend.md)
- [Notificações](document-notifications.md)
- [Revisão de entrega do registro binário](document-registration-delivery-review.md)
- [Spec do produto](../specs/01-produto/SPEC_PRODUTO.md)
- [Arquitetura e ADRs](../specs/02-arquitetura/ARQUITETURA.md)
