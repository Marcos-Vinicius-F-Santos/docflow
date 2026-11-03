# Contrato HTTP — Documentos

## Contrato lógico de notificações de ciclo de vida

As notificações de documentos usam um contrato JSON versionado e provider-neutral.
Ele é o mesmo contrato lógico que será entregue pelos canais de notificação; nomes de
exchange, filas, routing keys, storage, bucket e credenciais não fazem parte deste
payload.

Os cinco valores possíveis de `type` são:

- `DOCUMENT_PROCESSING_STARTED`;
- `DOCUMENT_COMPLETED`;
- `DOCUMENT_FAILED`;
- `DOCUMENT_DELETION_WARNING`;
- `DOCUMENT_DELETED`.

Exemplo de falha:

```json
{
  "eventId": "00000000-0000-0000-0000-000000000000",
  "schemaVersion": 1,
  "type": "DOCUMENT_FAILED",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "occurredAt": "2026-10-28T12:00:00Z",
  "status": "FAILED",
  "failureCount": 1
}
```

`eventId`, `schemaVersion`, `type`, `documentId` e `occurredAt` são obrigatórios em
todos os eventos. `status` é `PROCESSING` para início, `COMPLETED` para conclusão e
`FAILED` para falha/aviso de exclusão. `failureCount` é obrigatório em falha e aviso;
`exclusionScheduledAt` é obrigatório no aviso e pode ser omitido quando não houver
agenda aplicável. O evento `DOCUMENT_DELETED` não carrega estado do documento, pois o
registro já foi removido. Campos opcionais sem sentido são omitidos do JSON.

O contrato público não expõe `objectKey`, bucket, referência de staging, exchange, fila,
routing key, API key ou qualquer credencial.

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

## `POST /documents/{documentId}/retry`

Solicita explicitamente o reprocessamento assíncrono de um documento `FAILED`. A
requisição não recebe corpo e não aceita `objectKey`, `contentReference`, bucket,
credenciais ou qualquer outra referência de storage do cliente. O backend adquire lock
do documento, valida a elegibilidade e reutiliza o staging determinístico já existente
(`staging/{documentId}`); não é criado um novo arquivo ou documento.

O retry só é elegível quando o documento está em `FAILED`, `falhas < 3`,
`isExcluded: false` e o staging está disponível. Uma solicitação concorrente enquanto o
documento estiver em `PROCESSING` não publica uma segunda mensagem efetiva.

Quando a publicação do reprocessamento é confirmada, a resposta é `202 Accepted` com
`Content-Type: application/json` e o documento já observado em `PROCESSING`:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "originalFilename": "document.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 12345,
  "status": "PROCESSING",
  "createdAt": "2026-10-27T12:00:00Z",
  "updatedAt": "2026-10-27T12:05:00Z",
  "falhas": 1,
  "isExcluded": false,
  "exclusionScheduledAt": null
}
```

As propriedades de retry fazem parte da resposta de documento do Hub. `objectKey`,
bucket, credenciais e detalhes do SDK não fazem parte de nenhuma resposta HTTP.

O contador representa o ciclo atual de processamento. A falha efetiva do processamento
inicial conta como a primeira falha; clicar em retry não incrementa `falhas`. Quando o
processamento termina com sucesso, `falhas` volta para `0`, `isExcluded` permanece
`false` e `exclusionScheduledAt` volta para `null`.

| Situação | HTTP | `errorCode` |
|---|---:|---|
| Documento inexistente | `404 Not Found` | `DOCUMENT_NOT_FOUND` |
| Documento não está `FAILED`, já está marcado para exclusão, atingiu três falhas ou há outra condição de elegibilidade | `409 Conflict` | `DOCUMENT_RETRY_NOT_ELIGIBLE` |
| Staging/storage indisponível ou staging ausente | `503 Service Unavailable` | `DOCUMENT_STORAGE_UNAVAILABLE` |
| Publicação do retry sem confirmação | `503 Service Unavailable` | `DOCUMENT_RETRY_UNAVAILABLE` |

Quando a terceira falha efetiva é persistida, o documento continua em `FAILED`, recebe
`falhas: 3`, `isExcluded: true` e `exclusionScheduledAt` igual ao prazo de três horas.
O backend publica uma mensagem persistente de exclusão atrasada e o Hub não oferece uma
quarta tentativa. O usuário ainda pode chamar a exclusão individual antes do prazo;
essa operação é imediata e segue o contrato de `DELETE /documents/{documentId}`.

Erros usam `Content-Type: application/problem+json` e o formato `ProblemDetail`, com
`status`, `title`, `detail` operacional e `errorCode`. A mensagem não deve expor
bucket, `objectKey`, credenciais, stack trace ou detalhes do fornecedor.

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

Retorna uma coleção simples de `DocumentResponse` com todos os documentos persistidos
no contexto da instalação. Busca, filtros e paginação não fazem parte do contrato desta
feature.

Em caso de sucesso, responde `200 OK` com uma coleção JSON. Cada item inclui os campos
do `DocumentResponse` e os metadados operacionais do Hub:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000000",
    "originalFilename": "document.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 12345,
    "status": "COMPLETED",
    "createdAt": "2026-09-20T12:00:00Z",
    "updatedAt": "2026-09-20T12:00:01Z",
    "falhas": 0,
    "isExcluded": false,
    "exclusionScheduledAt": null
  }
]
```

Os quatro estados públicos (`PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`) podem
aparecer na coleção. Quando não houver documentos, o backend responde `200 OK` com
`[]`; a mensagem `Nenhum documento encontrado` é responsabilidade do frontend. A
Na coluna lateral do Hub, “Atividade” mostra no máximo os cinco documentos mais recentes
e “Atenção” mostra no máximo os cinco documentos `FAILED` mais recentes. Ambas usam
`updatedAt` decrescente, com fallback para `createdAt`.

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

## `GET /documents/{documentId}/preview`

Retorna o conteúdo binário do documento para visualização inline, usado pelo preview de
PDF na página de acompanhamento. O documento precisa estar em `COMPLETED` e segue as
mesmas regras de disponibilidade, streaming e erros de `GET /documents/{documentId}/content`.

Em caso de sucesso, responde `200 OK` com `Content-Type`, `Content-Length` e
`Cache-Control: no-store` equivalentes ao endpoint de download, mas usa:

- `Content-Disposition: inline` usando `originalFilename`.

O endpoint de download continua usando `Content-Disposition: attachment`.

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

## Notificações em tempo real

O Hub recebe os eventos de ciclo de vida por `GET /ws/notifications` em um upgrade
WebSocket. O contrato JSON, os cinco tipos de evento, a deduplicação por `eventId`, a
reconexão e a operação das filas estão descritos em
[document-notifications.md](document-notifications.md).

O WebSocket não oferece replay nem histórico. Depois de uma reconexão, o frontend
consulta `GET /documents` para recuperar o estado atual. Nomes de filas, routing keys,
storage e credenciais não fazem parte do payload entregue ao navegador.

| Situação | HTTP |
|---|---:|
| Documento inexistente | `404 Not Found` |
| Falha temporária do storage | `503 Service Unavailable` |
| Falha inesperada | `500 Internal Server Error` |

## `GET /logs`

Consulta somente leitura dos eventos operacionais persistidos. A resposta e paginada e
ordenada por `occurredAt` decrescente e, como desempate, por `eventId` decrescente.

Parametros opcionais:

| Parametro | Formato | Observacao |
|---|---|---|
| `page` | inteiro >= 0 | Pagina zero-based; padrao `0`. |
| `size` | inteiro entre `1` e `100` | Padrao `50`; limite controlado por `DOCFLOW_LOGS_MAX_PAGE_SIZE`. |
| `level` | `INFO`, `WARN` ou `ERROR` | Filtro por nivel. |
| `service`, `component`, `operation` | texto | Filtros por igualdade. |
| `documentId`, `correlationId` | UUID | Filtros por identificador. |
| `occurredFrom`, `occurredTo` | ISO-8601 `Instant` | Intervalo inclusivo; `occurredTo` nao pode preceder `occurredFrom`. |

Exemplo:

```text
GET /logs?page=0&size=25&level=ERROR&documentId=00000000-0000-0000-0000-000000000000
```

Resposta `200 OK`:

```json
{
  "items": [
    {
      "eventId": "00000000-0000-0000-0000-000000000000",
      "schemaVersion": 1,
      "occurredAt": "2026-10-29T12:00:00Z",
      "level": "ERROR",
      "service": "document-api",
      "component": "document-storage-consumer",
      "operation": "document.processing",
      "message": "Falha ao confirmar o storage final",
      "correlationId": null,
      "documentId": "00000000-0000-0000-0000-000000000000",
      "details": { "errorCode": "STORAGE_UNAVAILABLE" }
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Consultas invalidas respondem `400` com `application/problem+json`, `errorCode:
LOG_QUERY_INVALID`. Falha de acesso ao PostgreSQL responde `503` com
`errorCode: LOG_QUERY_UNAVAILABLE`; detalhes internos, SQL e stack trace nao sao
retornados. O endpoint nao permite editar, excluir ou reprocessar logs.
