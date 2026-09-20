# Registro de Decisão — Modelo persistido e claim da reconciliação de `RESULT_UNKNOWN`

Data: 2026-09-18  
Status: Aceita  

## Contexto

O DocFlow já persiste `reconciliation_attempts` pela V4 e possui um caso de uso que
consulta `exists(objectKey)`, mas não possui agenda persistida nem disparador
operacional. O scheduler aprovado precisa encontrar documentos `PROCESSING` elegíveis,
evitar execução duplicada e retomar o trabalho após reinício.

Além disso, o código atual só persiste `object_key` quando o documento chega a
`COMPLETED`. Um resultado `RESULT_UNKNOWN` durante o armazenamento final pode deixar um
documento `PROCESSING` sem a referência necessária para reconciliação.

## Decisão

Manter a reconciliação dentro do domínio `document` e reutilizar a tabela `documents`:

1. preservar `reconciliation_attempts` da V4;
2. adicionar somente a coluna mínima `reconciliation_next_attempt_at` se o schema
   existente não for suficiente;
3. persistir `object_key` determinístico antes de uma tentativa que possa resultar em
   `RESULT_UNKNOWN`, permitindo `exists(objectKey)` posterior;
4. usar lock de linha PostgreSQL com `FOR UPDATE SKIP LOCKED` como claim atômico durante
   a transação da tentativa, sem criar coluna de claim, tabela de histórico ou novo
   `DocumentStatus`;
5. manter `updated_at` exclusivamente como lease de processamento RabbitMQ;
6. tratar documentos `PROCESSING` existentes sem agenda como elegíveis para uma
   recuperação inicial, sem marcá-los automaticamente como sucesso ou falha.

O contador de reconciliação terá cinco tentativas e os atrasos aprovados de `1m`, `5m`,
`15m`, `30m` e `60m`. O scheduler incrementará o contador ao obter o lock, consultará
`exists(objectKey)` e então agendará a próxima tentativa, concluirá ou falhará o
documento conforme a Spec.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Criar uma tabela separada de reconciliações e claims | Adiciona estrutura e histórico não exigidos pela feature; aumenta o custo de consistência sem necessidade confirmada. |
| Adicionar um `reconciliation_status` ou estado `RECONCILING` | Cria um novo estado de negócio e amplia a máquina de estados sem necessidade. |
| Reutilizar `updated_at` como agenda de reconciliação | Mistura a lease RabbitMQ com o backoff do scheduler e pode permitir takeover incorreto. |
| Usar claim apenas em memória | Não evita duplicidade entre instâncias nem sobrevive ao reinício da aplicação. |
| Usar coluna de claim persistente com lease própria | É possível, mas adiciona um segundo lease e exige limpeza/expiração; o lock transacional resolve o escopo aprovado de uma tentativa. |

## Consequências

O modelo mantém a organização atual por domínio, usa PostgreSQL como autoridade para
agenda e exclusão, evita estados adicionais e permite recuperação após falha do
processo. A próxima data elegível fica observável e testável.

Como custo, a chamada `exists` ocorrerá dentro de uma transação que mantém lock de linha
por até o timeout do MinIO. A migration precisa backfillar `object_key` de documentos
`PROCESSING` antigos e identificar registros com contador já esgotado. Se o ambiente
futuro exigir tentativas longas, histórico completo ou claims independentes da
transação, esta decisão deverá ser substituída por novo ADR.
