# Banco de dados

## Migration V4 — reconciliação de `RESULT_UNKNOWN`

A migration `V4__add_reconciliation_attempts.sql` adiciona a coluna
`documents.reconciliation_attempts` como `BIGINT NOT NULL DEFAULT 0`.

Essa alteração é expand-only: as migrations `V1` a `V3` não devem ser editadas e os
documentos existentes recebem contador inicial igual a zero. O histórico completo de
tentativas não faz parte desta feature.

## Rollback operacional

Não remover a coluna em uma migration de rollback. Para voltar a uma versão anterior,
usar código compatível com a coluna adicional e reverter a versão da aplicação; a coluna
será ignorada pela versão anterior. A remoção da coluna, caso algum dia seja necessária,
deve ser tratada como uma migration própria, após confirmar que nenhum código depende
dela.

Antes de qualquer deploy, validar em um banco de homologação que o Flyway aplica `V4`
após `V3`, que registros existentes permanecem legíveis e que novos documentos começam
com zero tentativas.

## Migration V5 — agenda persistida da reconciliação

A migration `V5__schedule_result_unknown_reconciliation.sql` adiciona a coluna nullable
`documents.reconciliation_next_attempt_at` e um índice parcial para localizar documentos
`PROCESSING` elegíveis ao scheduler.

Ela também faz o backfill operacional de documentos já `PROCESSING`:

- quando `object_key` está nulo, preenche a referência determinística
  `documents/{documentId}`;
- quando a agenda está nula e o contador ainda é menor que cinco, torna o documento
  elegível imediatamente para uma tentativa de recuperação;
- preserva o contador `reconciliation_attempts` existente;
- não marca documentos como `COMPLETED` ou `FAILED` sem consultar o storage.

Antes do backfill, a migration aborta explicitamente se encontrar documento
`PROCESSING` com cinco ou mais tentativas registradas. Esse estado é inconsistente com a
política aprovada e exige inspeção operacional antes de reaplicar a migration; não há
falha silenciosa nem transição automática nesse caso.

Como a coluna é nullable e a alteração é expand-only, versões anteriores podem ignorá-la
durante rollback operacional. Não remover a coluna diretamente nem editar V1–V4; a
remoção futura exigiria uma migration própria após confirmar que nenhuma versão depende
dela.

Antes de qualquer deploy, validar em banco de homologação que o Flyway aplica V5 após
V4, que o backfill só afeta documentos `PROCESSING`, que a constraint impede agenda sem
`object_key` e que o índice parcial é criado.

## Migration V6 — retry e exclusão atrasada do Hub

A migration `V6__add_document_retry_and_deletion_metadata.sql` adiciona, de forma
expand-only, os metadados operacionais do retry:

- `documents.failure_count BIGINT NOT NULL DEFAULT 0`, exposto no domínio como
  `falhas`;
- `documents.is_excluded BOOLEAN NOT NULL DEFAULT FALSE`, exposto como `isExcluded`;
- `documents.exclusion_scheduled_at TIMESTAMPTZ NULL`, exposto como
  `exclusionScheduledAt`;
- índice parcial `idx_documents_exclusion_scheduled_at` para documentos marcados e
  elegíveis pelo prazo.

Os documentos existentes recebem `failure_count = 0`, `is_excluded = false` e
`exclusion_scheduled_at = NULL`. A migration também aplica as invariantes:

- `failure_count >= 0`;
- `is_excluded = false OR failure_count >= 3`;
- `exclusion_scheduled_at IS NULL OR is_excluded = true`.

V6 não edita V1–V5. O rollback é operacional: implantar código compatível que ignore as
colunas adicionais. Não remover colunas nem alterar dados diretamente; qualquer remoção
futura exige migration própria e revisão de dependências.

Antes de qualquer deploy, validar que o Flyway aplica V6 após V5, que registros
existentes permanecem legíveis com os defaults, que as três constraints estão ativas e
que o índice parcial cobre somente `is_excluded = true` com prazo preenchido. O fixture
de persistência está em
`backend/src/test/resources/contracts/hub/document-retry-persistence.json`.

### Ciclo persistido de falhas

O processamento inicial começa com `failure_count = 0`. Cada falha permanente de
processamento incrementa o contador e mantém o documento em `FAILED`. O retry manual
não incrementa o contador: ele adquire o lock, valida `FAILED`, `failure_count < 3`,
`is_excluded = false` e a existência do staging, muda o documento para `PROCESSING` e
publica a referência do staging existente.

Quando a terceira falha é persistida, a transição também define `is_excluded = true` e
`exclusion_scheduled_at = agora + 3 horas`. A publicação da mensagem de exclusão ocorre
depois do flush da alteração; o publisher exige confirmação. Não existe quarta tentativa
por retry manual. Se o processamento terminar com sucesso, o documento passa para
`COMPLETED`, `failure_count` volta para zero, `is_excluded` volta para `false` e o prazo
é limpo.

As consultas de retry e de exclusão usam lock da linha. O consumidor de exclusão só
remove o registro depois de remover o objeto final e o staging; uma mensagem posterior a
uma exclusão manual encontra o documento ausente e é tratada como idempotente.

## Operação e validação

- O Flyway aplica V1, V2, V3, V4, V5 e V6 na inicialização do backend.
- V4 persiste o contador de tentativas e V5 persiste a próxima elegibilidade e o índice
  usado pelo scheduler interno.
- V6 persiste o contador de falhas, a marcação de exclusão e o prazo usado pela fila de
  exclusão atrasada; o mapeamento JPA e as transições estão implementados no domínio
  `document`.
- A reconciliação usa `FOR UPDATE SKIP LOCKED` dentro do domínio `document`; não há
  tabela global de workers nem claim em memória.
- O retry manual usa lock da linha e não cria novo arquivo ou staging.
- A exclusão atrasada usa a fila RabbitMQ `docflow.document-deletion.delay.3h` com TTL
  de três horas; o timestamp persistido permanece como evidência e base para recuperação
  operacional.
- A execução local deve preservar os volumes e validar a ordem das migrations antes de
  qualquer deploy. `specs/05-verificacao/` registra evidências de validação, mas a
  Spec da Feature e este documento definem o comportamento e o rollback operacional.
- Nenhum deploy é autorizado sem revisão e aprovação explícita de Marcos.
