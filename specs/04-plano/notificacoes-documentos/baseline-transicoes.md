# Baseline dos pontos de transição — Notificações de documentos

Tarefa: T-002  
Status: concluída em 2026-10-29  
Spec: `specs/03-features/notificacoes-documentos/spec.md`  
Plano: `specs/04-plano/notificacoes-documentos/plano.md`  
ADR: `specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md`

## 1. Objetivo do registro

Este documento identifica os pontos reais em que o ciclo do documento muda de estado
ou remove recursos. Ele serve como baseline para as futuras publicações de eventos das
T-008 a T-012. A T-002 não altera transições, retry, reconciliação, agenda ou exclusão.

Regra de instrumentação para as próximas tarefas: publicar somente depois da operação
que justifica o evento ter sido aceita e persistida. Em caso de erro, retry, mensagem
duplicada ou falha de canal, o evento não pode antecipar o estado nem alterar o fluxo
principal.

## 2. Pontos de transição encontrados

| Evento/estado | Ponto atual | Ordem observada | Evento futuro permitido | Evidência existente |
|---|---|---|---|---|
| `PROCESSING` — ingestão inicial | `DocumentStorageMessageConsumer.process` chama `DocumentRepository.claimForProcessing(documentId, now)` | O `UPDATE` condicional grava `PROCESSING`, define `object_key` se necessário e avança `version`; somente retorno `1` possui a tentativa. Retorno `0` é duplicidade/lease de outro consumidor e não inicia novo processamento. | `DOCUMENT_PROCESSING_STARTED` depois do claim aceito e antes de continuar o processamento, sem emitir para claim recusado. | `DocumentStorageMessageConsumerTest.shouldClaimReadStoreRemoveAndCompleteTheDocument`, `shouldRequestRetryWhenAnotherConsumerOwnsTheLease`, `shouldAcknowledgeCompletedDuplicateWithoutReadingOrWriting`; `DocumentRepository.claimForProcessing`. |
| `PROCESSING` — retry explícito | `DocumentService.retry` chama `Document.startRetryProcessing`, `saveAndFlush` e publica nova solicitação de storage | A transição é persistida antes da publicação do pedido de retry. Falha ao abrir staging impede a transição; falha da publicação segue a política já existente. | `DOCUMENT_PROCESSING_STARTED` depois do `saveAndFlush` aceito, incluindo retry, sem contar uma nova falha antes do processamento. | `DocumentServiceTest`/`DocumentServiceIntegrationTest` sobre retry e `DocumentTest` sobre `startRetryProcessing`. |
| `COMPLETED` — storage final já existente | `DocumentStorageMessageConsumer.process`, ramo `documentStorage.exists(objectKey)` | Depois do claim, o consumidor marca `Document.markCompleted`, salva o documento e confirma a mensagem. Não lê staging nem remove staging nesse ramo. | `DOCUMENT_COMPLETED` somente depois de `markCompleted` e `repository.save`. | `DocumentStorageMessageConsumerTest.shouldNotStoreOrRemoveWhenTheFinalObjectAlreadyExists`. |
| `COMPLETED` — storage concluído nesta tentativa | `DocumentStorageMessageConsumer.process`, ramo `store` | O conteúdo é lido do staging, gravado no storage final, staging é removido, então `markCompleted` e `repository.save` persistem o estado; somente depois o handler pode confirmar a mensagem. | `DOCUMENT_COMPLETED` depois da persistência de `COMPLETED` e da confirmação do storage final; não emitir em resultado transitório/desconhecido. | `DocumentStorageMessageConsumerTest.shouldClaimReadStoreRemoveAndCompleteTheDocument`, `RabbitMqDocumentStorageMessageHandlerTest`. |
| `COMPLETED` — reconciliação | `DocumentReconciliationService.reconcileClaimedDocument` quando `documentStorage.exists(objectKey)` retorna verdadeiro | A tentativa é iniciada, o objeto é confirmado, `markCompleted` é chamado e o documento é salvo. | `DOCUMENT_COMPLETED` depois do `save`; não emitir antes da confirmação de existência. | `DocumentReconciliationServiceTest.shouldCompleteWhenStorageConfirmsObject`. |
| `FAILED` — falha permanente de storage | `DocumentStorageMessageConsumer.process` captura `DocumentStorageException` não retryable e chama `markFailed` | `Document.markFailed` incrementa `failureCount` e persiste `FAILED`; no terceiro erro usa `saveAndFlush` antes de publicar a agenda de exclusão. Falha de storage não deve ser confundida com falha do canal de notificação. | `DOCUMENT_FAILED` depois da persistência da falha e do contador correspondente. | `DocumentStorageMessageConsumerTest.shouldMarkTheDocumentFailedWhenStorageRejectsTheOperation`, `shouldMarkTheThirdEffectiveFailureAsExcludedAndScheduled`. |
| `FAILED` — esgotamento do retry RabbitMQ | `RabbitMqDocumentStorageMessageHandler` chama `markFailedAfterPermanentFailure` quando a fila de retry termina | A marcação final é feita pelo consumidor de storage e depois a mensagem é rejeitada para DLQ; a regra atual do documento permanece intacta. | `DOCUMENT_FAILED` somente depois de `markFailedAfterPermanentFailure` persistir a falha. | `RabbitMqDocumentStorageMessageHandlerTest`, `DocumentStorageMessageConsumerTest.shouldMarkTheDocumentFailedAfterRetryExhaustion` e `shouldMarkTheThirdEffectiveFailureAsExcludedAndScheduled`. |
| `FAILED` — reconciliação esgotada | `DocumentReconciliationService.reconcileClaimedDocument` quando `reconciliationAttempts >= maxAttempts` e o storage não confirma | `markFailed()` e `documentRepository.save(document)` persistem `FAILED`; a reconciliação não possui hoje um `DocumentDeletionPublisher` para publicar a agenda. | `DOCUMENT_FAILED` depois do `save`; qualquer publicação da agenda precisa ser tratada em tarefa própria sem antecipar a exclusão. | `DocumentReconciliationServiceTest.shouldFailAfterLastUnconfirmedAttempt`. |
| Agenda de exclusão — terceira falha | `Document.markFailed(Instant failedAt)` | Na terceira falha, `excluded=true` e `exclusionScheduledAt=failedAt + 3h`; no consumidor de storage, `saveAndFlush` ocorre antes de `DocumentDeletionPublisher.publish`. | `DOCUMENT_DELETION_WARNING` será agendado somente após a agenda persistida, usando a referência de três horas. A publicação do aviso não substitui a mensagem de exclusão existente. | `DocumentTest`, `DocumentStorageMessageConsumerTest.shouldScheduleDeletionOnlyWhenTheThirdFailureIsPersisted`, `DocumentDeletionMessageConsumerTest` sobre documento devido. |
| Exclusão automática — recursos completos | `DocumentDeletionMessageConsumer.process` após `findScheduledDeletionForUpdate` | Obtém lock da linha elegível; exclui objeto final; remove staging; exclui metadados; chama `flush`; só então retorna `ACKNOWLEDGE`. Qualquer falha de storage retorna `DEAD_LETTER` sem remover os recursos seguintes/metadados. | `DOCUMENT_DELETED` somente depois de `documentRepository.flush`; falha não publica exclusão concluída. | `DocumentDeletionMessageConsumerTest.shouldRemoveFinalObjectStagingAndMetadataOnlyAfterTheDueDocumentIsLocked`, `shouldDeadLetterWhenStorageDeletionFailsWithoutDeletingMetadata`. |
| Exclusão automática — mensagem duplicada/tardia | `DocumentDeletionMessageConsumer.process` quando o documento já não existe ou não está elegível | Documento ausente é reconhecido como duplicata; documento existente não elegível vai para DLQ. Nenhum storage é tocado nesses ramos. | Não emitir `DOCUMENT_DELETED` novamente para mensagem duplicada nem recriar documento. | `DocumentDeletionMessageConsumerTest.shouldAcknowledgeADeletionMessageWhenManualDeletionAlreadyRemovedTheDocument`, `shouldDeadLetterAStaleMessageForAnExistingDocument`. |
| Exclusão manual | `DocumentService.delete` | Obtém lock; exclui objeto final; remove staging; exclui metadados; chama `flush`; só retorna após o tratamento completo. Erro de storage interrompe antes da remoção de metadados. | `DOCUMENT_DELETED` somente depois do `flush`; falha manual não publica sucesso nem altera a regra de erro atual. | `DocumentServiceTest`, `DocumentServiceIntegrationTest` e `DocumentControllerTest` sobre DELETE. |

## 3. Componentes que serão instrumentados depois

| Ponto | Módulo de produção | Tarefa posterior |
|---|---|---|
| Contrato e publisher de ciclo de vida | novo `backend/.../document/messaging/rabbitmq/` | T-003, T-007 e T-013 |
| Claim inicial e retry aceito | `DocumentStorageMessageConsumer`, `DocumentService` | T-008 |
| Conclusão por storage e reconciliação | `DocumentStorageMessageConsumer`, `DocumentReconciliationService` | T-009 |
| Falha persistida e contador | `DocumentStorageMessageConsumer`, `DocumentReconciliationService` | T-010 |
| Agenda e aviso de exclusão | `Document.markFailed`, `DocumentStorageMessageConsumer`, topologia delay | T-011 |
| Exclusão manual/automática | `DocumentService`, `DocumentDeletionMessageConsumer` | T-012 |
| Entrega e isolamento de canais | consumidores de notificações, WebSocket e e-mail | T-013 a T-017 |

## 4. Lacunas e limites registrados

- A reconciliação após a última tentativa persiste a agenda de exclusão, mas não recebe
  hoje um `DocumentDeletionPublisher`; isso é uma lacuna do baseline e não será corrigido
  como efeito colateral da T-002.
- A publicação de storage existente não é um evento público de notificação e não deve
  ser reutilizada diretamente como contrato WebSocket/e-mail.
- O evento de início deve considerar tanto o claim inicial quanto o retry explícito;
  mensagens duplicadas ou claim recusado não geram novo evento.
- O evento de conclusão deve abranger storage duplicado já existente, storage recém-gravado
  e confirmação positiva da reconciliação.
- O evento de falha deve ser emitido para falha efetiva persistida, sem contar falha de
  WebSocket, e-mail, publisher de notificação ou indisponibilidade de canal.
- O aviso de exclusão depende de `exclusionScheduledAt` persistido; não haverá exclusão
  antecipada para criar o aviso.
- O evento de exclusão deve ser emitido depois do tratamento completo de storage final,
  staging e metadados, tanto no fluxo automático quanto no manual.
- Nenhuma alteração de schema, migration, histórico, outbox, autenticação ou comportamento
  de retry foi feita ou está autorizada por esta tarefa.

## 5. Verificação da T-002

- Os pontos de transição, persistência, ack/rejeição e ordem de remoção foram conferidos
  contra o código atual e os testes existentes.
- A lacuna da reconciliação foi registrada como divergência de baseline, sem alteração
  de comportamento.
- A suíte direcionada dos pontos mapeados passou antes do avanço para a T-003: **53
  testes**, zero falhas/erros, `BUILD SUCCESS`, cobrindo `DocumentTest`, `DocumentServiceTest`,
  `DocumentStorageMessageConsumerTest`, `DocumentReconciliationServiceTest` e
  `DocumentDeletionMessageConsumerTest`.
