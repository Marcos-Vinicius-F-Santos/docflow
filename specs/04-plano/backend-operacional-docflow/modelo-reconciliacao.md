# Modelo persistido da reconciliação de `RESULT_UNKNOWN`

Tarefa: T-002  
Feature: `specs/03-features/backend-operacional-docflow/spec.md`  
Status: Aprovado  
Registro relacionado: `specs/02-arquitetura/DECISAO/ADR-005-modelo-reconciliacao-result-unknown.md`

## 1. Objetivo do modelo

Permitir que o scheduler encontre documentos em `PROCESSING` cujo armazenamento final
teve resultado desconhecido, faça uma única tentativa de reconciliação por vez e retome
o fluxo após reinício da aplicação.

O modelo não cria um novo `DocumentStatus`, não cria uma tabela de histórico e não
mistura a lease do consumidor RabbitMQ com a agenda de reconciliação.

## 2. Campos e semântica

| Campo em `documents` | Tipo/estado | Semântica |
|---|---|---|
| `status` | enum existente | Continua usando `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`; nenhuma reconciliação cria um estado novo. |
| `object_key` | coluna existente, nullable | Deve conter a referência determinística `documents/{documentId}` antes de uma tentativa que possa exigir reconciliação. Continua nulo para `PENDING` ainda não publicado e permanece preenchido em `COMPLETED`. |
| `reconciliation_attempts` | `BIGINT NOT NULL DEFAULT 0`, V4 | Conta somente tentativas do scheduler que consultaram `exists(objectKey)`. Claims/retries RabbitMQ não incrementam esse contador. |
| `reconciliation_next_attempt_at` | `TIMESTAMPTZ NULL`, nova coluna se V4 não for suficiente | Data/hora a partir da qual o documento pode ser selecionado pelo scheduler. Nulo significa que a reconciliação não está agendada. |
| `version` | versão JPA existente | Continua protegendo atualizações concorrentes e não é substituído pelo contador de reconciliação. |
| `updated_at` | timestamp existente | Continua representando a lease de processamento RabbitMQ de 10 segundos; não será reutilizado como agenda de reconciliação. |

Não será criado um campo persistido `reconciliation_status` nem uma tabela de claims.
O estado mínimo de claim será a posse de um lock de linha PostgreSQL durante a transação
que seleciona, incrementa e conclui uma tentativa.

## 3. Regras de elegibilidade e contagem

Um documento é candidato ao scheduler quando:

```text
status = PROCESSING
object_key IS NOT NULL
reconciliation_next_attempt_at IS NOT NULL
reconciliation_next_attempt_at <= agora
reconciliation_attempts < 5
```

Ao obter o lock de linha, a aplicação incrementa `reconciliation_attempts` antes de
chamar `exists(objectKey)`. Assim, uma queda durante a consulta não permite repetir
silenciosamente a mesma tentativa como se ela nunca tivesse começado.

Os atrasos contratuais são:

| Tentativa | Data de elegibilidade | Resultado sem confirmação |
|---:|---|---|
| 1 | resultado desconhecido + `1 minuto` | agenda tentativa 2 para `+5 minutos` |
| 2 | tentativa 1 sem confirmação + `5 minutos` | agenda tentativa 3 para `+15 minutos` |
| 3 | tentativa 2 sem confirmação + `15 minutos` | agenda tentativa 4 para `+30 minutos` |
| 4 | tentativa 3 sem confirmação + `30 minutos` | agenda tentativa 5 para `+60 minutos` |
| 5 | tentativa 4 sem confirmação + `60 minutos` | move para `FAILED` e limpa a agenda |

Se `exists(objectKey)` confirmar o objeto em qualquer tentativa, o domínio move o
documento para `COMPLETED`, mantém `object_key` e limpa
`reconciliation_next_attempt_at`. A reconciliação nunca chama `store`.

Se `exists(objectKey)` não confirmar o objeto ou retornar uma falha inconclusiva, o
documento permanece `PROCESSING` enquanto houver tentativa restante e recebe a próxima
data elegível. Na quinta tentativa sem confirmação, vai para `FAILED`.

## 4. Claim atômico

O scheduler deve selecionar candidatos usando lock de linha PostgreSQL com comportamento
equivalente a `SELECT ... FOR UPDATE SKIP LOCKED`, dentro da mesma transação que executa
a tentativa de reconciliação.

Consequências do modelo:

- dois schedulers não obtêm a mesma linha ao mesmo tempo;
- o lock é liberado por commit ou rollback, inclusive se o processo morrer;
- não há uma coluna de claim que possa ficar presa após uma queda;
- `version` continua protegendo a gravação final contra uma atualização concorrente fora
  do fluxo esperado;
- a chamada `exists` fica dentro da transação da tentativa e deve respeitar o timeout
  MinIO de três segundos definido na Spec.

O lock de reconciliação não altera a lease de processamento RabbitMQ. O scheduler não
pode reivindicar um documento que ainda não tenha uma agenda de reconciliação persistida.

## 5. Documentos existentes

A migration posterior à V4 deve tratar dados existentes sem editar V1–V4:

- documentos não `PROCESSING` mantêm `reconciliation_next_attempt_at = NULL`;
- documentos `PROCESSING` sem `object_key` recebem a referência determinística
  `documents/{documentId}`, pois a arquitetura já define essa chave por UUID;
- documentos `PROCESSING` sem agenda recebem `reconciliation_next_attempt_at = agora`
  para uma tentativa de recuperação no primeiro ciclo do scheduler;
- `reconciliation_attempts` existente é preservado; não será zerado nem aumentado pela
  migration;
- a migration não marca documentos automaticamente como `COMPLETED` ou `FAILED` sem
  consultar o storage;
- documentos já com `reconciliation_attempts >= 5` devem ser identificados como dados
  inconsistentes antes da migration e tratados por uma decisão operacional explícita,
  não silenciosamente.

Esse backfill é parte da definição do modelo; a criação/aplicação da migration pertence
à T-003.

## 6. Limites desta definição

T-002 não implementa:

- migration nova;
- scheduler ou `@Scheduled`;
- query/lock no repositório;
- alteração no consumidor RabbitMQ;
- timeout do cliente MinIO;
- testes novos;
- marcação da tarefa como concluída.

Esses itens permanecem nas tarefas seguintes do plano.
