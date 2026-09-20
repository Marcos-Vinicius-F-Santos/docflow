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
