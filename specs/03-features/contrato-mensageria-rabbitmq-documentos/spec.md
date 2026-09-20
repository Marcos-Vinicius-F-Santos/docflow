# Spec da Feature — Contrato de mensageria RabbitMQ para o fluxo assíncrono de documentos

Status: Encerrada — consolidada em `registro-binario-e-armazenamento-assincrono-documentos`  
Projeto: DocFlow  
Onde vive no código: domínio `document`, com o Application Service publicando a
mensagem e os adaptadores de mensageria em `document/messaging/rabbitmq`; o consumidor
aciona a porta `DocumentStorage`, conforme os limites definidos em
`specs/02-arquitetura/ARQUITETURA.md`.

## 1. Objetivo

Definir o contrato lógico da mensagem que representa uma solicitação de armazenamento
assíncrono de um documento, desde a publicação pelo Application Service até o consumo e
a chamada da porta `DocumentStorage`.

Esta feature viabiliza separar a solicitação do processamento posterior e torna
explícitos os comportamentos mínimos quando a publicação ou o consumo falhar. A
ingestão do conteúdo binário e sua gravação no staging já são fornecidas pela etapa
anterior do fluxo; esta feature consome o `contentReference` produzido por ela.

## 2. Como funciona hoje

Esta spec foi encerrada e consolidada em `registro-binario-e-armazenamento-assincrono-documentos`
e `backend-operacional-docflow`. O código atual publica e consome o contrato v1 em
RabbitMQ, com publisher confirm, consumidor manual `prefetch=1`, concorrência `1..1`,
retries de `5s/15s/60s`, DLX/DLQ, requeue e tratamento explícito de `REJECTED` e
`RESULT_UNKNOWN`. A Spec consolidada e a Spec operacional são as fontes primárias.

## 3. Requisitos funcionais

### FR-001

QUANDO o Application Service solicitar o processamento assíncrono do armazenamento de um
documento  
O SISTEMA DEVE publicar no RabbitMQ uma mensagem que represente essa solicitação.

### FR-002

QUANDO a mensagem de solicitação de armazenamento for publicada  
O SISTEMA DEVE transportar um JSON `DocumentStorageRequested` com `schemaVersion`,
`documentId`, `objectKey`, `contentReference`, `sizeBytes` e `contentType`.

### FR-003

QUANDO o consumidor receber uma mensagem válida de solicitação de armazenamento  
O SISTEMA DEVE verificar primeiro `DocumentStorage.exists(objectKey)` e, se o objeto já
existir, remover imediatamente o staging referenciado por `contentReference`, tratar a
solicitação como já concluída e não chamar `store` novamente; caso não exista, deve
resolver `contentReference` e traduzir o contrato para uma chamada à porta
`DocumentStorage.store`, sem expor o consumidor ao SDK ou a detalhes específicos do
MinIO.

### FR-004

QUANDO a chamada de `DocumentStorage.store` terminar com sucesso  
O SISTEMA DEVE considerar o armazenamento final confirmado, remover o objeto de staging
referenciado por `contentReference` somente depois dessa confirmação e confirmar o
consumo após o processamento bem-sucedido.

### FR-005

QUANDO a publicação da mensagem falhar  
O SISTEMA DEVE aguardar publisher confirm síncrono, informar a falha ao Application
Service quando não houver confirmação clara e não deve reportar a solicitação como aceita
para processamento assíncrono.

### FR-006

QUANDO o consumidor não conseguir processar a mensagem — por contrato inválido ou por
falha na chamada de `DocumentStorage.store`  
O SISTEMA DEVE sinalizar o processamento como malsucedido, não deve confirmá-lo como
concluído e deve tornar a falha observável; mensagens inválidas devem ir diretamente para
a DLQ, enquanto falhas transitórias devem seguir três retries além da entrega inicial,
com uma fila de retry para cada atraso de `5s`, `15s` e `60s`, e depois serem
encaminhadas para a DLQ.

## 4. Regras de negócio

### BR-001

A mensagem é uma solicitação de processamento de armazenamento; ela não representa, por
si só, a confirmação de que o documento foi armazenado.

### BR-002

O identificador do documento deve permitir correlacionar a mensagem, o processamento e
a chamada de `DocumentStorage`.

### BR-003

O `objectKey` transportado pela mensagem deve ser a referência estável usada na gravação
do objeto. Conforme a decisão arquitetural vigente, ele é baseado no UUID do documento.

### BR-004

Antes de gravar, o consumidor deve verificar `DocumentStorage.exists(objectKey)`. Se o
objeto existir, deve remover imediatamente o staging e não deve repetir `store`, podendo
concluir o consumo de forma idempotente.

### BR-005

O publisher deve usar confirmação síncrona do RabbitMQ; ausência de confirmação deve ser
tratada como falha e não pode ser convertida silenciosamente em sucesso da solicitação
assíncrona.

### BR-006

Falha de consumo não pode ser convertida silenciosamente em processamento concluído.
Mensagem inválida não deve ser retentada; falha transitória deve usar três retries além
da entrega inicial, nas filas de retry de `5s`, `15s` e `60s`, antes da DLQ.

### BR-007

O objeto de staging referenciado por `contentReference` só pode ser removido depois que o
armazenamento no `objectKey` final for confirmado. A exceção é o caminho idempotente em
que `exists(objectKey)` já retorna verdadeiro: nesse caso, o staging deve ser removido
imediatamente. Falha no armazenamento final não pode remover o staging.

### BR-008

O Application Service e o consumidor não devem depender diretamente de classes do SDK do
RabbitMQ ou do MinIO para expressar as regras do fluxo; esses detalhes pertencem aos
adaptadores.

### BR-009

Publicação, início do consumo, sucesso e falha devem gerar log estruturado contendo
`documentId` como identificador de correlação.

## 5. Contrato lógico da mensagem

A mensagem de solicitação deve fornecer semanticamente os seguintes dados ao consumidor:

| Campo lógico | Obrigatoriedade | Uso |
|---|---|---|
| `schemaVersion` | Obrigatório | Identificar a versão do contrato; a versão inicial é `1`. |
| `documentId` | Obrigatório | Correlacionar a solicitação ao documento. |
| `objectKey` | Obrigatório | Identificar a referência estável do objeto a ser gravado. |
| `contentReference` | Obrigatório | Referenciar o conteúdo disponível no objeto de staging para a leitura pelo consumidor. |
| `sizeBytes` | Obrigatório | Informar o tamanho recebido por `DocumentStorage.store`. |
| `contentType` | Obrigatório | Informar o tipo de conteúdo recebido por `DocumentStorage.store`. |

O formato serializado é JSON. A mensagem não transporta o binário; `contentReference`
deve permitir recuperar o conteúdo de staging antes da chamada ao storage final.

Exemplo do contrato lógico na versão inicial:

```json
{
  "schemaVersion": 1,
  "documentId": "00000000-0000-0000-0000-000000000000",
  "objectKey": "documents/00000000-0000-0000-0000-000000000000",
  "contentReference": "staging/00000000-0000-0000-0000-000000000000",
  "sizeBytes": 12345,
  "contentType": "application/pdf"
}
```

O exchange principal é durável e do tipo `direct`, a fila principal é durável, as
mensagens são persistentes (delivery mode 2) e a routing key é
`document.storage.requested`. O fluxo de falha usa três filas de retry, uma para cada
atraso de `5s`, `15s` e `60s`, seguidas por uma DLX/DLQ; mensagem inválida vai
diretamente para a DLQ.

## 6. Critério de aceite

### AC-001 — caminho feliz: publicação, consumo e limpeza do staging

Dado um documento com uma mensagem JSON `DocumentStorageRequested` v1 válida e um objeto
de staging disponível em `contentReference`  
Quando o Application Service publicar a solicitação com confirmação do RabbitMQ e o
consumidor receber a mensagem, verificar que `objectKey` ainda não existe, gravar o
conteúdo no storage final e remover o staging  
Então o consumidor deve chamar `DocumentStorage.store` com os dados correspondentes e
confirmar o consumo somente após o armazenamento final retornar com sucesso e a limpeza
do staging ser executada.

### AC-002 — caminho de erro: falha na publicação

Dado que o Application Service solicitou o armazenamento assíncrono de um documento  
Quando o RabbitMQ falhar durante a publicação  
Então a falha deve ser informada ao Application Service e a solicitação não deve ser
reportada como aceita para processamento assíncrono.

### AC-003 — caminho de erro: mensagem inválida

Dado que o consumidor recebeu uma mensagem sem um ou mais dados obrigatórios do contrato  
Quando tentar processá-la  
Então não deve acionar `DocumentStorage.store`, deve encaminhar a mensagem diretamente
para a DLQ, sem retry, e não deve confirmar o consumo como concluído.

### AC-004 — caminho de erro: falha no storage

Dado que o consumidor recebeu uma mensagem válida  
Quando `DocumentStorage.store` falhar de forma transitória  
Então o consumidor não deve confirmar o consumo como concluído, deve aplicar até três
tentativas com backoff e deve encaminhar a mensagem para a DLQ quando o limite for
atingido, sem remover o staging antes do armazenamento final ser confirmado.

### AC-005 — caminho idempotente: objeto final já existente

Dado que o consumidor recebeu uma mensagem válida, o objeto final existe e há um
staging referenciado por `contentReference`  
Quando processar a solicitação  
Então deve remover imediatamente o staging, não deve chamar `DocumentStorage.store`
novamente e deve confirmar o consumo como já concluído.

## 7. Casos de erro / edge cases

- RabbitMQ indisponível, publicação rejeitada ou publisher confirm ausente → tratar como
  falha de publicação e não reportar aceitação assíncrona.
- Mensagem sem `schemaVersion`, `documentId`, `objectKey`, `contentReference`,
  `sizeBytes` ou `contentType` → encaminhar diretamente para a DLQ, sem retry e sem
  chamar `DocumentStorage.store`.
- `contentReference` indisponível no momento do consumo → tratar como falha de consumo,
  aplicar a política de retry limitado e não remover o staging.
- `DocumentStorage.exists(objectKey)` verdadeiro → remover imediatamente o staging e não
  repetir `store`.
- Falha transitória no storage → usar as filas de retry de `5s`, `15s` e `60s` e depois
  encaminhar para DLQ.
- `REJECTED` → não é retryable e, quando o documento é identificável, leva a `FAILED`
  antes do encaminhamento definitivo à DLQ.
- `UNAVAILABLE` → seguir a política de retry da mensagem.
- `RESULT_UNKNOWN` → não converter em sucesso; mantém o documento em `PROCESSING` para
  retry RabbitMQ e posterior reconciliação pelo scheduler.
- Entrega duplicada → usar `exists(objectKey)` antes de `store` como ponto de idempotência;
  concorrência entre consumidores ainda requer teste e avaliação.

## 8. Fora de escopo desta feature

- Implementar o recebimento do conteúdo binário pela API REST.
- Definir ou implementar o adaptador concreto de MinIO.
- Definir os nomes operacionais específicos da DLX/DLQ e das três filas de retry.
- Alterar a feature anterior de ingestão do binário e gravação no staging.
- Definir a política de transição do documento entre `PENDING`, `PROCESSING`,
  `COMPLETED` e `FAILED` em cada resultado do storage.
- A política de reconciliação de `RESULT_UNKNOWN` é definida pela Spec consolidada
  `backend-operacional-docflow` e pelo ADR-005.
- Criar uma transação distribuída entre PostgreSQL, RabbitMQ e o storage.
- Alterar endpoints REST ou criar uma nova API pública.

## 9. Suposições e perguntas abertas

- [x] Decisão confirmada: o contrato é JSON, usa `schemaVersion` explícito e a versão
  inicial é `1`; a mensagem se chama `DocumentStorageRequested`.
- [x] Decisão confirmada: os campos são `schemaVersion`, `documentId`, `objectKey`,
  `contentReference`, `sizeBytes` e `contentType`.
- [x] Decisão confirmada: o conteúdo é referenciado, não transportado no payload; a
  referência aponta para conteúdo de staging, que só deve ser removido após confirmação
  do armazenamento final.
- [x] Decisão confirmada: o exchange é `direct` e durável, a fila é durável, as mensagens
  são persistentes e a routing key é `document.storage.requested`.
- [x] Decisão confirmada: publisher confirms são síncronos; ausência de confirmação é
  falha e não haverá outbox nesta rodada.
- [x] Decisão confirmada: mensagens inválidas vão diretamente para DLQ; falhas
  transitórias usam retry limitado com backoff e depois DLQ.
- [x] Decisão confirmada: `exists(objectKey)` é o ponto de idempotência antes de `store`;
  o consumidor remove imediatamente o staging e não grava novamente quando o objeto final
  já existe.
- [x] Decisão confirmada: três retries ocorrem além da entrega inicial, usando filas
  separadas para os atrasos de `5s`, `15s` e `60s`, antes do encaminhamento à DLQ.
- [x] Decisão confirmada: endpoint e credenciais do MinIO são fornecidos pelas
  variáveis `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY`; as credenciais
  root são exclusivas do Compose/bootstrap.
- [x] Decisão confirmada: a ingestão do binário e a gravação no staging já estão
  configuradas com MinIO e são fornecidas por uma etapa anterior; esta feature não cria
  outro ponto de entrada para conteúdo.
- [x] Decisão confirmada: a observabilidade mínima é log estruturado com `documentId` em
  publicação, início de consumo, sucesso e falha; tracing distribuído e métricas
  dedicadas ficam fora desta rodada.
- [x] Decisão consolidada: `REJECTED` identificável leva o documento a `FAILED` antes da
  DLQ; `RESULT_UNKNOWN` preserva `PROCESSING` para retry e reconciliação.
- [x] Decisão consolidada: a entrada binária, o staging e a leitura provider-neutral
  são fornecidos pela feature consolidada de registro binário.
- [x] Decisão consolidada: os nomes operacionais são `docflow.document-storage.dlx`,
  `docflow.document-storage.dlq` e as filas `retry.5s`, `retry.15s` e `retry.60s`.
- [x] Decisão consolidada: o timeout de publisher confirm é `PT3S`, compartilhado com
  o deadline operacional aprovado.
- [x] Decisão consolidada: `contentReference` usa `staging/{documentId}` e é lido pela
  porta `DocumentStagingReader`.
- [x] Decisão consolidada: o claim PostgreSQL e a política de consumidor `1/prefetch=1`
  evitam duplicidade no processamento efetivo; o ADR-005 define o claim da reconciliação.

## 10. Definition of Done desta feature

- [ ] Critério de aceite (seção 6) satisfeito
- [ ] Casos de erro de prioridade alta tratados
- [ ] Features existentes continuam funcionando (regressão checada)
- [ ] Testado seguindo `specs/05-verificacao/plano-de-teste-template.md`
- [ ] Eu revisei e aprovei antes do deploy
