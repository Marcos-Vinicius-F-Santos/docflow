# Contrato HTTP — Documentos

## `POST /documents`

O registro de documentos exige `multipart/form-data` com uma única parte obrigatória:

- `file`: conteúdo binário; o nome do arquivo e o `Content-Type` vêm dos headers da
  própria parte.

O formato JSON anterior, contendo somente `originalFilename`, `contentType` e
`sizeBytes`, não faz mais parte do contrato e deve ser rejeitado com HTTP `400`.
O tamanho persistido é calculado a partir do conteúdo efetivamente recebido.

O `GET /documents/{documentId}` permanece disponível com o contrato de resposta existente.

Em caso de sucesso, `POST /documents` responde `201 Created`, envia `Location:
/documents/{id}` e mantém o corpo `DocumentResponse`, com `status: PENDING` e o
`sizeBytes` contado do stream:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "originalFilename": "document.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 12345,
  "status": "PENDING"
}
```

Falhas de validação retornam HTTP `400` com `Content-Type: application/problem+json`.
O corpo usa `ProblemDetail` e inclui `errorCode`; `invalidField` aparece quando a falha
se refere à parte `file`.

| `errorCode` | `invalidField` padrão | Significado |
|---|---|---|
| `DOCUMENT_CONTENT_MISSING` | `file` | Parte `file` ausente. |
| `DOCUMENT_CONTENT_EMPTY` | `file` | Conteúdo vazio. |
| `DOCUMENT_FILENAME_MISSING` | `file` | Filename ausente ou em branco. |
| `DOCUMENT_CONTENT_TYPE_MISSING` | `file` | Content type ausente ou em branco. |
| `DOCUMENT_CONTENT_TYPE_MISMATCH` | `file` | Assinatura detectada incompatível com o content type declarado. |
| `DOCUMENT_CONTENT_TYPE_UNKNOWN` | `file` | Assinatura não identificada. |
| `DOCUMENT_CONTENT_TOO_LARGE` | `file` | Conteúdo acima de `52428800` bytes. |
| `DOCUMENT_METADATA_NOT_ALLOWED` | `metadata` | Partes adicionais ou metadata duplicada não são aceitas. |
| `DOCUMENT_CONTENT_READ_FAILED` | — | Falha ao abrir ou ler o conteúdo. |

## Operação do recebimento

O tamanho máximo aprovado é `52428800` bytes. O tamanho persistido é contado durante a
gravação do stream no bucket separado `docflow-staging`; o cliente não envia
`sizeBytes`.

O staging usa a configuração `MINIO_STAGING_BUCKET`, com valor padrão
`docflow-staging`. Credenciais e endpoint do MinIO devem ser fornecidos pelo ambiente;
não fazem parte deste contrato.

O procedimento de inicialização, smoke test, correlação por `documentId` e tratamento
de falha de publicação está em
[document-registration-operations.md](document-registration-operations.md).

O registro retorna `PENDING` após staging e publicação confirmada. O processamento
assíncrono pode terminar em `COMPLETED` quando o storage final for confirmado, ou em
`FAILED` para falha permanente. `RESULT_UNKNOWN` não é sucesso: mantém o documento em
`PROCESSING`, sem confirmação indevida da entrega, e segue retries RabbitMQ e a
reconciliação interna conforme a Spec de `backend-operacional-docflow`.

Depois da gravação, a mensageria deve transportar somente `contentReference`,
`documentId`, `objectKey`, `sizeBytes` e `contentType`. A configuração operacional do
bucket, da mensageria e do rollback está em
[document-registration-operations.md](document-registration-operations.md).

## `GET /documents`

Retorna a coleção paginada de documentos persistidos. O endpoint oficial continua sendo
`GET /documents`; busca, filtros, paginação e ordenação são query parameters da mesma
operação.

### Query parameters

| Parâmetro | Tipo | Regra |
|---|---|---|
| `search` | string | Busca parcial (`contains`) case-insensitive em `originalFilename`. |
| `status` | string | Filtra por um dos estados públicos: `PENDING`, `PROCESSING`, `COMPLETED` ou `FAILED`. |
| `contentType` | string | Filtra pelo `contentType` persistido. |
| `createdFrom` / `createdTo` | ISO-8601 UTC | Intervalo semiaberto `[from,to)` sobre `createdAt`. |
| `updatedFrom` / `updatedTo` | ISO-8601 UTC | Intervalo semiaberto `[from,to)` sobre `updatedAt`. |
| `page` | inteiro | Página 1-based; padrão `1`. O tamanho é fixo em `10`. |
| `sort` | string | Campo e direção, no formato `campo,direcao`. Todos os sete campos são permitidos. |

Parâmetros vazios são ignorados. A combinação de filtros preenchidos usa AND. A
ordenação padrão é `updatedAt,desc`, com desempate por `createdAt,desc` e `id,asc`.
O backend converte a página pública 1-based para o índice interno utilizado pelo
Spring Data.

Em caso de sucesso, responde `200 OK` com um envelope JSON. Cada item de `content` usa
os sete campos do `DocumentResponse`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "originalFilename": "document.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 12345,
      "status": "COMPLETED",
      "createdAt": "2026-09-20T12:00:00Z",
      "updatedAt": "2026-09-20T12:00:01Z"
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Os quatro estados públicos (`PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`) podem
aparecer na coleção. Quando não houver documentos, o backend responde `200 OK` com
`content: []`, `totalElements: 0` e `totalPages: 0`; a mensagem
`Nenhum documento encontrado` é responsabilidade do frontend.

Parâmetros inválidos respondem `400 Bad Request` em `application/problem+json`, com
`errorCode: DOCUMENT_QUERY_INVALID`. Uma falha inesperada responde com `500 Internal
Server Error` no mesmo envelope, sem expor detalhes de PostgreSQL ou infraestrutura.

## `POST /documents/batch-download`

Solicita o download em lote dos documentos identificados no corpo:

```json
{
  "documentIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```

Em caso de sucesso, responde `200 OK` com um arquivo ZIP transmitido em streaming:

- `Content-Type: application/zip`;
- `Content-Disposition: attachment; filename="documents.zip"`;
- `Cache-Control: no-store`;
- `Content-Length` pode ser omitido quando não puder ser calculado sem materializar o
  arquivo inteiro.

Somente documentos `COMPLETED` entram no ZIP. Itens `PENDING`, `PROCESSING`, `FAILED`,
inexistentes ou indisponíveis no storage são ignorados e devem ser informados ao usuário.
Quando houver itens válidos e ignorados, a resposta usa o header
`X-DocFlow-Skipped-Documents` com os UUIDs ignorados separados por vírgula. O frontend
correlaciona os UUIDs com os itens selecionados para exibir seus filenames.

Se nenhum item puder ser incluído, a operação responde `409 Conflict` em
`application/problem+json`, com `errorCode: DOCUMENT_CONTENT_NOT_AVAILABLE` e a
propriedade `skippedDocumentIds`. O backend nunca expõe `objectKey`, bucket ou
credenciais.

## `DELETE /documents`

Solicita uma exclusão definitiva em lote. A confirmação explícita é responsabilidade do
frontend e deve ocorrer antes desta requisição. O corpo contém os IDs:

```json
{
  "documentIds": [
    "00000000-0000-0000-0000-000000000000"
  ]
}
```

Em caso de requisição válida, responde `200 OK` com resultado individual, inclusive
quando todos os itens forem excluídos:

```json
{
  "results": [
    {
      "documentId": "00000000-0000-0000-0000-000000000000",
      "status": "DELETED"
    },
    {
      "documentId": "11111111-1111-1111-1111-111111111111",
      "status": "FAILED",
      "errorCode": "DOCUMENT_STORAGE_UNAVAILABLE",
      "message": "Não foi possível excluir este documento."
    }
  ]
}
```

Os status por item são `DELETED`, `FAILED` e `NOT_FOUND`. Uma falha de storage ou de
exclusão não remove o item da listagem e usa a mensagem padrão correspondente. Uma
requisição inválida responde `400` em `application/problem+json`; falha inesperada de
contrato responde no mesmo envelope. A operação individual continua usando
`DELETE /documents/{documentId}` e `204 No Content`.

A exclusão em lote aplica o ADR-006 individualmente a cada documento: lock pessimista,
remoção idempotente do objeto final e do staging, remoção posterior do registro e
tratamento de mensagens RabbitMQ tardias sem recriar o documento. Não existe exclusão
lógica nem cancelamento RabbitMQ.

## `GET /documents/{documentId}/content`

Retorna o conteúdo binário do documento para download. O documento precisa estar em
`COMPLETED`; `PENDING`, `PROCESSING` e `FAILED` não possuem conteúdo final disponível
para este contrato.

Em caso de sucesso, responde `200 OK` e transmite o conteúdo em streaming com:

- `Content-Type` igual ao `contentType` persistido;
- `Content-Length` igual ao `sizeBytes` persistido;
- `Content-Disposition: attachment` usando `originalFilename`;
- `Cache-Control: no-store`.

O `objectKey` permanece interno ao backend. A aplicação deve ler o objeto por uma porta
provider-neutral, sem carregar o binário inteiro em memória.

### Contrato interno de leitura (não HTTP)

O serviço de aplicação fornece à porta provider-neutral somente a chave interna do objeto
final e recebe um conteúdo aberto para leitura, com:

- `InputStream` do conteúdo;
- `sizeBytes` esperado;
- `contentType` persistido.

O adaptador é responsável por abrir o stream no MinIO. O componente que inicia a resposta
HTTP é responsável por consumir e fechar o stream, inclusive quando a transmissão falha.
Nenhuma camada deve transformar o conteúdo inteiro em `byte[]` como contrato obrigatório,
nem retornar credenciais, bucket ou `objectKey` ao cliente.

| Situação | HTTP | `errorCode` |
|---|---:|---|
| Documento inexistente | `404` | `DOCUMENT_NOT_FOUND` |
| Status diferente de `COMPLETED` | `409` | `DOCUMENT_CONTENT_NOT_AVAILABLE` |
| Storage indisponível | `503` | `DOCUMENT_STORAGE_UNAVAILABLE` |
| Falha inesperada | `500` | código genérico |

## `DELETE /documents/{documentId}`

Solicita a exclusão definitiva do documento. A confirmação da ação ocorre no frontend;
este endpoint só é chamado depois da confirmação explícita do usuário.

Em caso de sucesso, responde `204 No Content`. A operação remove o registro persistido
e os objetos associados conforme a política do
[ADR-006 — Exclusão definitiva de documentos](../specs/02-arquitetura/DECISAO/ADR-006-exclusao-definitiva-documentos.md).

O endpoint não recebe confirmação no corpo: a confirmação é uma responsabilidade da
interface. A operação usa lock pessimista no registro, remove primeiro os objetos final
e staging e remove o registro somente depois. Se uma remoção falhar, o documento
permanece disponível para nova tentativa e a resposta não é de sucesso. Uma mensagem
RabbitMQ que chegar depois da remoção do registro não recria o documento nem grava
conteúdo; segue a política de documento ausente do ADR-006.

O endpoint não implementa exclusão lógica e não recebe `objectKey` do cliente.

| Situação | HTTP |
|---|---:|
| Documento inexistente | `404 Not Found` |
| Falha temporária do storage | `503 Service Unavailable` |
| Falha inesperada | `500 Internal Server Error` |
