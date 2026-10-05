# Tarefas — Política de reconciliação para resultado desconhecido do storage (`RESULT_UNKNOWN`)

Plano relacionado: specs/04-plano/politica-reconciliacao-result-unknown/plano.md

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente pra revisar de uma vez, referenciar o
requisito (FR-XXX) que ela implementa, e dizer como verificar que ficou pronta.

## Fase 1 — Base

- [x] T-001 [FR-005] Confirmar o modelo de persistência do contador de tentativas e criar a migration Flyway `V4` sem alterar `V1`, `V2` ou `V3`.
  - Arquivos/módulos: `backend/src/main/resources/db/migration/V4__add_reconciliation_attempts.sql`, decisão sobre coluna em `documents` ou tabela de histórico.
  - Verificação: executar o Flyway contra um banco limpo e contra um banco com as migrations atuais; confirmar que registros existentes continuam legíveis e que o contador pode ser persistido.

- [x] T-002 [FR-005] Atualizar o mapeamento de persistência do documento para ler e salvar o contador definido na `V4`.
  - Arquivos/módulos: `document`/domínio e adaptador de persistência conforme a organização arquitetural adotada; `Document.java`, `DocumentRepository.java` ou equivalentes.
  - Verificação: teste de integração salva um contador, recarrega o documento e obtém o mesmo valor sem alterar o comportamento dos campos existentes.

- [x] T-003 [FR-001][FR-002][FR-003][FR-004] Definir o ponto de entrada interno da camada de aplicação para a reconciliação, sem criar endpoint, scheduler ou consumidor RabbitMQ.
  - Arquivos/módulos: novo caso de uso em `backend/src/main/java/com/dockflow/dockflow/document/application/`; dependências somente em domínio, persistência e porta `DocumentStorage`.
  - Verificação: compilação e teste estrutural confirmam que o caso de uso não importa SDK do MinIO, controller, RabbitMQ ou mecanismo de agendamento.

## Fase 2 — Lógica principal

- [x] T-004 [FR-001] Garantir que um documento em `PROCESSING` após `RESULT_UNKNOWN` não seja promovido diretamente para `COMPLETED`.
  - Arquivos/módulos: caso de uso de reconciliação e métodos de domínio de `Document`.
  - Verificação: teste unitário inicia um documento em `PROCESSING`, simula resultado desconhecido e confirma que o estado permanece `PROCESSING` e que `markCompleted` não é chamado.

- [x] T-005 [FR-002] Implementar a confirmação positiva usando somente `DocumentStorage.exists(objectKey)` e mover o documento para `COMPLETED` quando o objeto existir.
  - Arquivos/módulos: caso de uso em `document/application`, porta `DocumentStorage` e domínio `Document`.
  - Verificação: teste unitário simula `exists(objectKey) = true`, verifica a transição para `COMPLETED`, a referência estável e a persistência do documento.

- [x] T-006 [FR-003][FR-005] Implementar a tentativa sem confirmação mantendo o documento em `PROCESSING` e atualizando o contador quando ainda houver tentativas disponíveis.
  - Arquivos/módulos: caso de uso, estado de `Document` e adaptador de persistência.
  - Verificação: teste unitário simula `exists(objectKey) = false` com tentativas restantes e verifica estado `PROCESSING`, contador incrementado e possibilidade de nova reconciliação.

- [x] T-007 [FR-004][FR-005] Implementar o encerramento por esgotamento das tentativas, movendo o documento para `FAILED` e persistindo o resultado.
  - Arquivos/módulos: caso de uso, domínio `Document` e persistência PostgreSQL.
  - Verificação: teste unitário e teste de integração simulam a última tentativa sem confirmação e verificam estado `FAILED`, contador persistido e nenhuma promoção para `COMPLETED`.

- [x] T-008 [FR-002][FR-003] Preservar o uso do adaptador existente para `exists`, sem repetir `store` e sem introduzir chamada a `delete`.
  - Arquivos/módulos: `DocumentStorage.java`, `MinioDocumentStorage.java` e caso de uso.
  - Verificação: teste com mock/contrato confirma que a reconciliação chama `exists(objectKey)` e não chama `store` nem `delete`.

## Fase 3 — Interface

- [x] T-009 [FR-001][FR-002][FR-003][FR-004] Expor apenas o ponto de entrada interno da aplicação para futura chamada por outro mecanismo, sem interface REST ou mensageria nesta feature.
  - Arquivos/módulos: `document/application`; nenhuma alteração em `DocumentController`, DTOs ou `document/messaging/rabbitmq`.
  - Verificação: revisar o diff e confirmar que não foram adicionados endpoint, mensagem, fila, scheduler, publisher, consumer ou contrato público novo.

## Fase 4 — Testes

- [x] T-010 [FR-001][FR-002] Cobrir o caminho de `RESULT_UNKNOWN` e a confirmação positiva por `exists(objectKey)`.
  - Arquivos/módulos: `backend/src/test/java/com/dockflow/dockflow/document/`.
  - Verificação: testes comprovam `PROCESSING` enquanto não há confirmação e `COMPLETED` quando `exists` retorna `true`.

- [x] T-011 [FR-003][FR-004] Cobrir ausência de confirmação com tentativas restantes e com tentativas esgotadas.
  - Arquivos/módulos: testes unitários do caso de uso e do domínio.
  - Verificação: os cenários comprovam, respectivamente, `PROCESSING` com contador atualizado e `FAILED` no limite, sem transição indevida para `COMPLETED`.

- [x] T-012 [FR-005] Cobrir a migration `V4` e a persistência do contador em PostgreSQL.
  - Arquivos/módulos: `DocumentServiceIntegrationTest.java` ou novo teste de integração no domínio `document`; Testcontainers/PostgreSQL.
  - Verificação: a suíte aplica todas as migrations, persiste o contador, recarrega o documento e mantém os documentos existentes válidos.

- [x] T-013 [FR-002][FR-003] Verificar o contrato do adaptador MinIO para `exists`: objeto presente retorna `true`, objeto ausente retorna `false` e falha inconclusiva não é tratada como confirmação.
  - Arquivos/módulos: teste do `MinioDocumentStorage` e `DocumentStorageException`.
  - Verificação: testes confirmam o mapeamento de `statObject` e que somente a existência confirmada habilita `COMPLETED`.

- [x] T-014 [FR-001][FR-002][FR-003][FR-004][FR-005] Executar a regressão do backend e revisar os critérios de aceite da Spec.
  - Arquivos/módulos: suíte existente em `backend/src/test/java/` e Spec relacionada.
  - Verificação: testes unitários, integração e contexto da aplicação passam; registro explícito de qualquer divergência entre Spec, plano, código e testes.

## Fase 5 — Entrega

- [x] T-015 [FR-001][FR-002][FR-003][FR-004][FR-005] Validar migration, rollback operacional, documentação do resultado e revisão final antes de deploy.
  - Arquivos/módulos: migration `V4`, documentação de operação aplicável e revisão do diff.
  - Verificação: confirmar ordem do Flyway, compatibilidade com dados existentes, caminho de recuperação e aprovação de Marcos; não executar deploy antes dessa aprovação.

## Adiado (fora do escopo desta rodada)

- [ ] Definir e implementar scheduler, consumer RabbitMQ, comando manual ou outro disparador da reconciliação.
- [ ] Definir o intervalo e a estratégia de backoff entre tentativas.
- [ ] Repetir a gravação do conteúdo durante a reconciliação.
- [ ] Adicionar validação por tamanho, `contentType` ou checksum.
- [ ] Limpar objetos com `delete(objectKey)` após `FAILED`.
- [ ] Criar endpoint REST ou contrato público para disparar/consultar a reconciliação.
- [ ] Implementar o fluxo completo de recebimento e armazenamento do conteúdo binário.
