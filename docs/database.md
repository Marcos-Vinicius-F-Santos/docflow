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

## Operação e validação

- O Flyway aplica V1, V2, V3, V4 e V5 na inicialização do backend.
- V4 persiste o contador de tentativas e V5 persiste a próxima elegibilidade e o índice
  usado pelo scheduler interno.
- A reconciliação usa `FOR UPDATE SKIP LOCKED` dentro do domínio `document`; não há
  tabela global de workers nem claim em memória.
- A execução local deve preservar os volumes e validar a ordem das migrations antes de
  qualquer deploy. `specs/05-verificacao/` registra evidências de validação, mas a
  Spec da Feature e este documento definem o comportamento e o rollback operacional.
- Nenhum deploy é autorizado sem revisão e aprovação explícita de Marcos.
