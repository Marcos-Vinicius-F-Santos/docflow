# Spec da Feature — Registro binário e armazenamento assíncrono de documentos

Status: Aprovada por Marcos  
Projeto: DocFlow  
Consolida: `entrada-binaria-documentos` e `contrato-mensageria-rabbitmq-documentos`

## 1. Objetivo

Implementar em uma única feature o fluxo completo de registro e armazenamento
assíncrono de documentos:

1. receber o conteúdo binário pelo `POST /documents` como `multipart/form-data`;
2. traduzir a parte binária para um contrato interno independente de Spring Web;
3. validar e gravar o conteúdo em staging durável no MinIO;
4. publicar no RabbitMQ somente a referência ao staging;
5. consumir a mensagem, ler o staging por `contentReference` e gravar o conteúdo no
   storage final por meio da porta `DocumentStorage`;
6. remover o staging somente depois da confirmação do armazenamento final.

A consolidação elimina a dependência circular entre a entrada binária e a mensageria:
API, staging, publisher, consumidor e leitura do staging passam a ser entregues e
validados como um único fluxo.

## 2. Como funciona hoje

O `POST /documents` recebe multipart com `file`, grava o conteúdo em staging no MinIO,
persiste o documento como `PENDING` e publica a referência no RabbitMQ. O consumidor
usa o contrato v1, claim de processamento e a porta provider-neutral de staging para
gravar o storage final.

O comportamento complementar de timeout, `RESULT_UNKNOWN`, falhas permanentes,
reconciliação, concorrência explícita e bootstrap está definido na Spec aprovada
`backend-operacional-docflow`; esta Spec continua sendo a fonte do contrato de entrada
binária e armazenamento assíncrono.

## 3. Requisitos funcionais

### FR-001 — Entrada multipart

O SISTEMA DEVE aceitar `POST /documents` como `multipart/form-data` com uma parte
obrigatória chamada `file`. O filename e o `Content-Type` devem vir dos headers da
própria parte, sem parte `metadata` duplicando esses dados.

### FR-002 — Contrato interno de conteúdo

O controller DEVE traduzir a parte `file` para um contrato próprio contendo
`InputStream`, filename e content type. `MultipartFile` não pode atravessar o limite da
entrada REST para a aplicação ou o domínio.

### FR-003 — Stream sem bufferização integral

O fluxo DEVE representar o conteúdo com `InputStream`, sem usar `byte[]` ou arquivo
temporário da aplicação como contrato interno.

### FR-004 — Validação do conteúdo

O SISTEMA DEVE validar arquivo ausente, filename, content type, conteúdo vazio e
assinatura dos bytes reais com Apache Tika. Assinatura divergente ou não identificada
deve resultar em HTTP `400`.

### FR-005 — Limite e tamanho real

O limite máximo deve ser `52428800` bytes. O `sizeBytes` persistido e publicado deve
ser obtido durante a leitura real do stream com `CountingInputStream`, e não declarado
separadamente pelo cliente.

### FR-006 — Erros da API

Falhas de validação devem retornar HTTP `400` com `application/problem+json`, usando
`ProblemDetail` e as propriedades `errorCode` e `invalidField` quando aplicável.

### FR-007 — Quebra do contrato JSON

O `POST /documents` JSON somente com metadados deve ser rejeitado com HTTP `400`, pois o
binário passa a ser obrigatório.

### FR-008 — Staging antes da publicação

Após a validação, o Application Service DEVE gravar o conteúdo em um bucket de staging
separado no MinIO durante a própria requisição HTTP, antes de publicar a mensagem.

### FR-009 — Referência durável

O objeto de staging DEVE usar uma referência lógica no formato `staging/{documentId}`.
O `InputStream` original deve ser fechado pelo componente que concluir a gravação.

### FR-010 — Mensagem sem binário

O publisher DEVE enviar somente JSON com `schemaVersion`, `documentId`, `objectKey`,
`contentReference`, `sizeBytes` e `contentType`. A mensagem nunca pode transportar
`InputStream` ou o binário.

### FR-011 — Confirmação da publicação

O publisher DEVE usar confirmação síncrona do RabbitMQ. Sem confirmação clara, o
Application Service deve tratar a publicação como falha e não reportar o registro como
aceito para processamento assíncrono.

### FR-012 — Leitura do staging

O consumidor DEVE resolver `contentReference` por uma porta provider-neutral de staging,
abrir o conteúdo e traduzi-lo para uma chamada à porta `DocumentStorage`, sem expor o
consumidor ao SDK do MinIO.

### FR-013 — Idempotência do storage final

Antes de gravar o storage final, o consumidor DEVE verificar
`DocumentStorage.exists(objectKey)`. Se o objeto já existir, não deve chamar `store`
novamente e pode concluir a mensagem de forma idempotente.

### FR-014 — Limpeza após confirmação

O consumidor só pode remover o objeto de staging depois que o armazenamento final for
confirmado com sucesso.

### FR-015 — Falha de consumo

Mensagem inválida deve ir diretamente para a DLQ, sem retry. Falhas transitórias do
storage ou da leitura do staging devem ter a entrega inicial e mais três retries. Cada
retry deve usar uma fila de atraso própria, com atrasos de 5 segundos, 15 segundos e 60
segundos, e depois ser encaminhado para a DLQ, sem confirmação prematura. A transição
do documento para `FAILED` e a preservação de `PROCESSING` em `RESULT_UNKNOWN` são
definidas pela Spec `backend-operacional-docflow`.

### FR-016 — Observabilidade

Publicação, início do consumo, sucesso e falha devem produzir log estruturado contendo
`documentId` para correlação.

### FR-017 — Idempotência e concorrência

Antes de ler o staging e gravar o storage final, o consumidor DEVE obter um claim
atômico do documento, preferencialmente pela transição protegida
`PENDING → PROCESSING` no PostgreSQL. Uma entrega duplicada que encontrar o documento
como `PROCESSING` ou `COMPLETED` não deve chamar `store` novamente.

## 4. Regras de negócio e arquitetura

- O Application Service coordena o fluxo, mas não importa Spring Web, MinIO ou classes
  específicas do RabbitMQ.
- O controller é o único ponto que conhece `MultipartFile`.
- O staging deve ser uma porta provider-neutral própria, separada de `DocumentStorage`,
  porque possui bucket, referência e ciclo de vida intermediários próprios.
- O adaptador de staging usa o cliente MinIO e o bucket `docflow-staging`.
- O storage final continua sendo acessado por `DocumentStorage`, sem alterar o significado
  das operações existentes `store`, `exists` e `delete`.
- O `objectKey` final deve ser determinístico por documento e o armazenamento final deve
  ser idempotente para esse identificador.
- `DocumentStorage.exists(objectKey)` é uma defesa adicional, não o mecanismo de
  sincronização; o claim atômico é o mecanismo que evita a corrida entre consumidores.
- Uma lease de `10000 ms` por tentativa deve permitir recuperar documentos que ficaram em
  `PROCESSING` após a queda de um consumidor; o takeover só ocorre após a expiração.
- PostgreSQL, MinIO e RabbitMQ não formam uma transação distribuída.
- O registro só pode retornar sucesso depois da gravação do staging e da confirmação da
  publicação.
- O documento permanece sujeito às transições de estado já definidas; a política
  detalhada para `RESULT_UNKNOWN` continua na feature de reconciliação.

## 5. Contrato lógico da mensagem

Routing key: `document.storage.requested`  
Exchange recomendado: `docflow.document-storage`  
Fila principal recomendada: `docflow.document-storage.requested`  
DLX recomendada: `docflow.document-storage.dlx`  
DLQ recomendada: `docflow.document-storage.dlq`  
Mensagem: persistente, delivery mode 2  
Schema inicial: versão `1`

Timeout máximo de processamento de uma entrega: `3000 ms`. Esse timeout pertence ao
processamento da mensagem e não substitui o TTL das filas de retry.

Lease recomendado para um claim em `PROCESSING`: `10000 ms`, iniciado a cada tentativa.
O valor deixa margem de aproximadamente 3× sobre o timeout de processamento para
encerramento, confirmação e variação de agendamento. A retomada só pode ocorrer quando
o lease estiver expirado.

Filas de retry recomendadas, cada uma ligada ao DLX com TTL/roteamento para a fila
principal:

- `docflow.document-storage.retry.5s`
- `docflow.document-storage.retry.15s`
- `docflow.document-storage.retry.60s`

Os nomes usam o prefixo do aplicativo e o contexto funcional, evitando nomes genéricos
que possam colidir com outros projetos no mesmo broker. São recomendações operacionais
para aprovação, não nomes já existentes no ambiente.

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

Os nomes da topologia, o backoff, o timeout máximo de processamento e o lease estão
definidos nesta Spec; sua implementação operacional ainda precisa ser testada no broker
e no ambiente de execução.

## 6. Critérios de aceite

### AC-001 — caminho feliz completo

**Dado** um cliente enviando um documento válido como multipart  
**Quando** o sistema validar o conteúdo, gravar o staging, publicar com confirmação,
consumir a mensagem, ler `contentReference` e gravar o storage final  
**Então** deve persistir o tamanho real, responder somente após a publicação confirmada,
armazenar o conteúdo final e remover o staging apenas depois dessa confirmação.

### AC-002 — arquivo ausente

**Dado** um `POST /documents` sem a parte `file`  
**Quando** o controller processar a requisição  
**Então** deve responder HTTP `400` em `application/problem+json`, sem persistir,
publicar ou gravar staging.

### AC-003 — conteúdo inválido ou acima do limite

**Dado** uma parte `file` vazia, incompatível com o content type, sem assinatura
reconhecida ou maior que `52428800` bytes  
**Quando** a validação for executada  
**Então** deve responder HTTP `400` com `errorCode`, sem publicar a mensagem.

### AC-004 — falha na publicação

**Dado** que o staging foi gravado  
**Quando** o RabbitMQ não confirmar a publicação  
**Então** o Application Service deve informar a falha e não responder como aceito para
processamento assíncrono; o staging deve ser preservado para tratamento operacional.

### AC-005 — mensagem inválida

**Dado** uma mensagem sem campo obrigatório ou com schema inválido  
**Quando** o consumidor tentar processá-la  
**Então** não deve chamar `DocumentStorage.store`, deve enviar a mensagem diretamente
para a DLQ e não deve confirmá-la como processada com sucesso.

### AC-006 — falha transitória no storage

**Dado** uma mensagem válida e staging disponível  
**Quando** o storage final falhar de forma transitória  
**Então** o consumidor deve aplicar retry limitado com backoff, manter o staging e
encaminhar para a DLQ após o limite, sem confirmação prematura.

### AC-007 — objeto final já existente

**Dado** uma mensagem válida cujo `objectKey` já existe  
**Quando** o consumidor verificar a idempotência  
**Então** não deve chamar `store` novamente, deve preservar o staging para reconciliação
e deve confirmar o processamento como já concluído.

### AC-008 — consumidores concorrentes

**Dado** duas entregas da mesma solicitação disponíveis para consumidores concorrentes  
**Quando** ambos tentarem obter o claim do mesmo documento  
**Então** somente um deve obter a transição `PENDING → PROCESSING`; o outro deve tratar
a entrega como duplicada, não chamar `store` e não remover o staging.

## 7. Fora de escopo

- Frontend Angular.
- Nova migration ou alteração do schema PostgreSQL.
- Transação distribuída ou outbox.
- Política de reconciliação para `RESULT_UNKNOWN`.
- Autenticação, autorização, antivírus e lifecycle automático do bucket.
- Alteração das regras da máquina de estados além do necessário para este fluxo.

## 8. Suposições e perguntas abertas

- [x] O endpoint público será `POST /documents` com uma única parte `file`.
- [x] O conteúdo será representado por `InputStream`, sem `byte[]` como contrato.
- [x] O staging usará bucket separado `docflow-staging` e referência
  `staging/{documentId}`.
- [x] O publisher e o consumidor serão provider-neutral nas camadas de aplicação.
- [x] A mensagem será JSON persistente, com schema inicial `1` e os seis campos
  definidos na seção 5.
- [x] O storage final será acessado pela porta `DocumentStorage` existente.
- [x] A ADR-004 sobre staging assíncrono está aceita.
- [x] Nomes operacionais aprovados: exchange `docflow.document-storage`, fila
  `docflow.document-storage.requested`, DLX `docflow.document-storage.dlx`, DLQ
  `docflow.document-storage.dlq` e filas de retry `retry.5s`, `retry.15s` e `retry.60s`.
- [x] “Três retries” significa a entrega inicial mais três reentregas, totalizando
  quatro entregas possíveis antes da DLQ.
- [x] O backoff usa filas separadas para 5 segundos, 15 segundos e 60 segundos,
  selecionadas pelo contador de retries.
- [x] O timeout máximo de processamento de uma mensagem é `3000 ms`; o TTL das filas de
  retry não o substitui.
- [x] Se `exists(objectKey)` retornar verdadeiro, o staging deve ser preservado para
  reconciliação.
- [x] A corrida entre consumidores será tratada com consumidor idempotente, `objectKey`
  determinístico e claim atômico no PostgreSQL, preferencialmente pela transição
  protegida `PENDING → PROCESSING`. O `exists(objectKey)` permanece como defesa, não como
  sincronização; duplicatas em `PROCESSING`/`COMPLETED` não chamam `store` e preservam o
  staging.
- [x] Lease operacional aprovado: `10000 ms` por tentativa, com takeover somente após
  expiração. Um consumidor concorrente que encontrar um lease válido não deve confirmar
  a mensagem como sucesso; deve aguardar/reencaminhar conforme a política de retry.
- [x] Variáveis MinIO aprovadas: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`,
  `MINIO_SECRET_KEY` e `MINIO_STAGING_BUCKET`. As variáveis `MINIO_ROOT_USER` e
  `MINIO_ROOT_PASSWORD` permanecem específicas do bootstrap do servidor MinIO e não
  devem ser assumidas automaticamente como credenciais da aplicação.

## 9. Definition of Done

- [ ] Os critérios de aceite da seção 6 passam ponta a ponta.
- [ ] `POST /documents` multipart está presente no commit promovido.
- [ ] O staging possui escrita e leitura reais por referência.
- [ ] Publisher, topologia e consumidor RabbitMQ estão implementados e testados.
- [ ] A mensagem não transporta binário.
- [ ] Falhas de validação, publicação, leitura e storage têm o tratamento definido.
- [ ] Testes prioritários e regressão do backend passam.
- [ ] Checklist `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes` é revisado novamente.
- [ ] Marcos aprova antes do deploy.
