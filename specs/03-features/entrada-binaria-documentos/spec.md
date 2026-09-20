# Spec da Feature — Recebimento de conteúdo binário no registro de documentos

Status: Encerrada — consolidada em `registro-binario-e-armazenamento-assincrono-documentos`  
Projeto: DocFlow  
Onde vive no código: entrada REST do domínio `document`, com tradução no controller
para um contrato interno próprio consumido pelo Application Service, respeitando os
limites definidos em `specs/02-arquitetura/ARQUITETURA.md`.

## 1. Objetivo

Alterar o `POST /documents` para receber `multipart/form-data` com o conteúdo binário do
documento.

A entrada REST deve traduzir a parte binária do Spring para um contrato interno próprio
— por exemplo, `DocumentContent` com `InputStream`, `filename` e `contentType` — antes
de chamar o Application Service. O processamento será assíncrono: o conteúdo será
gravado em um bucket de staging durável durante a requisição, e somente a referência ao
objeto será publicada na mensageria.

## 2. Como funciona hoje

O `POST /documents` recebe um JSON com `originalFilename`, `contentType` e `sizeBytes`.
O sistema valida esses campos, persiste os metadados no PostgreSQL e cria o documento
no estado `PENDING`.

O `DocumentRegistrationRequest` atual é um `record` usado como `@RequestBody` JSON e
não possui conteúdo binário. Também não existe ponto de entrada interno com o binário
disponível.

Esta feature altera o contrato existente: depois dela, o `POST /documents` exigirá
`multipart/form-data` e conteúdo binário; a solicitação JSON somente com metadados não
será mais aceita.

## 3. Requisitos funcionais

### FR-001

QUANDO um cliente solicitar o registro de um documento pelo `POST /documents`  
O SISTEMA DEVE aceitar uma requisição `multipart/form-data` com uma parte obrigatória
`file`; o `filename` e o `Content-Type` da própria parte devem fornecer,
respectivamente, o `originalFilename` e o `contentType` do documento. Não deve existir
uma parte `metadata` separada para duplicar esses dados ou `sizeBytes`.

### FR-002

QUANDO o controller receber uma requisição multipart válida  
O SISTEMA DEVE traduzir a parte `file` do Spring para um contrato interno próprio,
contendo um `InputStream`, o `filename` e o `contentType` da parte. O `sizeBytes` deve
ser obtido durante a leitura do stream para o staging.

### FR-003

QUANDO o Application Service processar o registro de um documento com conteúdo binário  
O SISTEMA DEVE receber o contrato interno próprio, e não uma dependência direta de
`MultipartFile` ou de outro tipo específico do Spring Web.

### FR-004

QUANDO uma solicitação de registro for processada  
O SISTEMA DEVE representar o conteúdo por `InputStream`, sem carregar o arquivo inteiro
em um `byte[]` como contrato interno do fluxo.

### FR-005

QUANDO o binário estiver ausente, o `Content-Type` estiver vazio, o Tika não identificar
uma assinatura específica, o tipo for inconsistente com a assinatura dos bytes reais,
ou o tamanho real recebido exceder `52428800` bytes  
O SISTEMA DEVE detectar o tipo do conteúdo pela assinatura dos bytes com Apache Tika,
compará-lo ao `Content-Type` declarado e rejeitar a requisição com HTTP `400` quando
alguma dessas condições de erro ocorrer.

### FR-006

QUANDO a requisição multipart passar pelas validações de entrada  
O SISTEMA DEVE gravar o conteúdo em staging durável e encaminhar ao fluxo de mensageria
somente a referência desse objeto, sem transportar o binário na mensagem.

### FR-007

QUANDO o `POST /documents` receber uma requisição JSON somente com
`originalFilename`, `contentType` e `sizeBytes`  
O SISTEMA DEVE rejeitá-la com HTTP `400`, pois o conteúdo binário é obrigatório.

### FR-008

QUANDO o sistema rejeitar a requisição por uma falha de validação de entrada  
O SISTEMA DEVE responder em `application/problem+json`, usando o formato
`ProblemDetail` do Spring Boot 3.

### FR-009

QUANDO a requisição multipart passar pelas validações de entrada  
O SISTEMA DEVE gravar o `InputStream` recebido em um objeto de staging durável no MinIO,
em bucket separado do bucket final, durante a própria requisição HTTP e antes de
publicar a mensagem de mensageria.

### FR-010

QUANDO a gravação do staging terminar com sucesso  
O SISTEMA DEVE fechar o `InputStream` original e publicar na mensagem somente a referência
do conteúdo — por exemplo, `staging/{documentId}` — nunca o stream ou o binário.

### FR-011

QUANDO o conteúdo for gravado no staging por meio de um stream decorado com
`CountingInputStream`  
O SISTEMA DEVE usar a contagem acumulada durante a única leitura para definir o
`sizeBytes` do documento, sem usar `byte[]` ou arquivo temporário da aplicação.

### FR-012

QUANDO o sistema responder um erro HTTP `400` por campo ausente ou inválido  
O SISTEMA DEVE incluir no `ProblemDetail` a propriedade extensível `errorCode` com um
valor textual estável e, quando aplicável, a propriedade `invalidField` com o nome do
campo inválido.

### FR-013

QUANDO o staging e a publicação da mensagem forem realizados  
O SISTEMA DEVE manter a requisição HTTP em processamento até concluir a gravação do
staging e a publicação da mensagem; a resposta HTTP só deve ser retornada depois dessas
duas etapas.

## 4. Regras de negócio

### BR-001

O contrato público do `POST /documents` é `multipart/form-data` e exige conteúdo
binário na parte `file`.

### BR-002

O controller é responsável por traduzir tipos do Spring Web para o contrato interno;
`MultipartFile` não deve atravessar o limite até o Application Service ou o domínio.

### BR-003

O contrato interno deve usar `InputStream` para representar o conteúdo, acompanhado do
tamanho real recebido e do `contentType` da parte `file`.

### BR-004

Uma requisição sem conteúdo binário, com `contentType` inválido ou com tamanho real acima
de `52428800` bytes não é um registro válido e deve resultar em HTTP `400` com
`application/problem+json`.

### BR-005

O limite máximo de tamanho deve ser aplicado antes de aceitar a requisição para o fluxo
posterior e é de `52428800` bytes (50 MiB).

### BR-006

O processamento posterior é assíncrono. O componente de entrada deve gravar o conteúdo
no staging antes de publicar a mensagem; a feature de mensageria recebe somente uma
referência durável e nunca um `InputStream` amarrado à requisição HTTP.

### BR-007

O `sizeBytes` persistido nos metadados deve ser calculado a partir do conteúdo realmente
recebido pelo `CountingInputStream`, durante a gravação no staging, e não declarado
separadamente pelo cliente.

### BR-008

O conteúdo deve sobreviver ao encerramento da requisição HTTP: o staging deve ser gravado
antes da publicação da mensagem, e a mensagem deve transportar somente o
`contentReference`.

### BR-009

O staging deve ser acessado por uma porta provider-neutral da aplicação; o Application
Service não deve depender diretamente do cliente ou do SDK do MinIO. O adaptador deve
usar o cliente MinIO já existente e o bucket separado `docflow-staging`, distinto do
bucket final.

### BR-010

Os bytes reais devem ser inspecionados com Apache Tika por meio de
`TikaInputStream.get(stream)`, preservando o conteúdo para a gravação posterior mesmo
quando o stream original não suportar `mark/reset`. Ausência de assinatura específica
ou divergência entre a assinatura detectada e o `Content-Type` declarado invalida a
requisição.

### BR-011

Os erros de validação devem usar `ProblemDetail` em `application/problem+json`, mantendo
`errorCode` como string estável e `invalidField` como propriedade opcional. Não deve
existir envelope adicional fora do RFC 7807.

## 5. Critério de aceite

### AC-001 — caminho feliz

Dado um cliente que envia `POST /documents` como `multipart/form-data` com
uma parte `file` contendo `filename`, `Content-Type` e conteúdo binário válido  
Quando o controller processar a requisição  
Então deve criar o contrato interno próprio com `InputStream`, tamanho real recebido e
`contentType`, e chamar o Application Service sem repassar `MultipartFile`.

### AC-002 — caminho de erro: binário obrigatório

Dado um cliente que envia `POST /documents` sem a parte obrigatória `file`  
Quando a requisição for processada  
Então o sistema deve responder HTTP `400` em `application/problem+json` e não deve
encaminhar a solicitação à estratégia de processamento posterior.

### AC-003 — caminho de erro: validação do conteúdo

Dado um cliente que envia uma requisição multipart cujo `Content-Type` está vazio ou
inconsistente com o conteúdo, ou cujo tamanho real excede `52428800` bytes  
Quando a requisição for validada  
Então o sistema deve responder HTTP `400` em `application/problem+json` e não deve
encaminhar o conteúdo à estratégia de processamento posterior.

### AC-004 — caminho de erro: contrato anterior

Dado um cliente que envia o formato JSON anterior contendo somente os três metadados
  
Quando chamar `POST /documents`  
Então o sistema deve responder HTTP `400`, pois o novo contrato exige
`multipart/form-data` com conteúdo binário.

### AC-005 — limite de tamanho

Dado um cliente que envia uma parte `file` com tamanho real de `52428800` bytes  
Quando a requisição for processada  
Então o sistema deve aceitar o conteúdo, desde que as demais validações sejam satisfeitas.

### AC-006 — validação por assinatura

Dado que o cliente envie uma parte `file` com `Content-Type` declarado  
Quando o sistema detectar o tipo pelos bytes reais usando Tika com
`TikaInputStream.get(stream)`  
Então deve comparar a assinatura detectada com o `Content-Type` declarado e rejeitar
com HTTP `400` quando houver divergência ou quando nenhuma assinatura específica for
identificada.

### AC-007 — staging antes da mensageria

Dado que o cliente tenha enviado uma requisição multipart válida  
Quando o sistema processar o conteúdo recebido  
Então deve gravar o `InputStream` em um objeto de staging durável no MinIO durante a
requisição, fechar o stream após a escrita e publicar uma mensagem contendo apenas o
`contentReference`, como `staging/{documentId}`, antes de retornar a resposta HTTP.

### AC-008 — falha no staging

Dado que o cliente tenha enviado uma requisição multipart válida  
Quando a gravação do conteúdo no staging falhar  
Então o sistema não deve publicar a mensagem de mensageria e deve tratar a falha conforme
o contrato de erro do fluxo de entrada.

### AC-009 — detalhes do erro de validação

Dado que uma requisição seja rejeitada por ausência ou invalidade de um campo  
Quando o sistema construir a resposta HTTP `400`  
Então deve responder em `application/problem+json` com um `ProblemDetail` que contenha
`errorCode` como string estável e, quando aplicável, `invalidField` com o valor `file`.

## 6. Casos de erro / edge cases

- Parte binária ausente → responder `400`.
- Parte binária vazia → responder `400`, salvo decisão posterior explícita em contrário.
- `Content-Type` ausente ou vazio → responder `400`.
- `Content-Type` inconsistente com o conteúdo → responder `400`; detectar o tipo por
  assinatura dos bytes reais usando Apache Tika.
- Tika sem assinatura específica identificada → tratar como divergência e responder
  `400`.
- Conteúdo com tamanho real acima de `52428800` bytes → responder `400`.
- Requisição JSON somente com metadados → responder `400`.
- Falha no fluxo posterior depois que a entrada foi validada → seguir a spec de
  mensageria de documentos.
- Falha ao gravar o staging → não publicar a mensagem; o tratamento da resposta HTTP e
  eventual limpeza ficam sujeitos ao contrato do fluxo de entrada.
- O `InputStream` original deve ser fechado pelo componente que grava o staging assim que
  a escrita terminar.
- Falha ao abrir ou ler o `InputStream` durante a tradução/encaminhamento → tratar como
  erro da requisição e não publicar a mensagem.

## 7. Fora de escopo desta feature

- Reaproveitar o `DocumentRegistrationRequest` atual como `@RequestBody` JSON.
- Definir o formato detalhado da documentação OpenAPI além do contrato desta feature.
- Usar `byte[]` como representação interna do conteúdo.
- Criar migration ou alterar schema do banco.
- Persistir o conteúdo final do documento em PostgreSQL, MinIO ou outro storage; a
  gravação em staging é apenas a ponte até a mensageria.
- Redefinir o contrato, a topologia ou o tratamento de falhas da mensageria; esses pontos
  pertencem à spec de mensageria de documentos.
- Alterar a máquina de estados do documento além do comportamento já definido para o
  fluxo posterior.
- Definir autenticação, autorização, segurança antivírus ou políticas de retenção do
  conteúdo.

## 8. Suposições e perguntas abertas

- [x] Comportamento atual confirmado: o `POST /documents` recebe JSON com os três
  metadados, valida, persiste no PostgreSQL e cria o documento em `PENDING`.
- [x] Fato confirmado: o `DocumentRegistrationRequest` atual possui somente
  `originalFilename`, `contentType` e `sizeBytes`.
- [x] Fato confirmado: atualmente não existe ponto de entrada interno com o binário
  disponível.
- [x] Decisão confirmada: o `POST /documents` passará a aceitar
  `multipart/form-data` com metadados e conteúdo binário.
- [x] Decisão confirmada: será usado um DTO próprio de request multipart; o
  `DocumentRegistrationRequest` atual não será reaproveitado.
- [x] Decisão confirmada: o controller traduzirá `MultipartFile` para um contrato
  interno próprio antes de chamar o Application Service.
- [x] Decisão confirmada: o contrato interno usará `InputStream`, acompanhado de
  tamanho e `contentType`, e não `byte[]`.
- [x] Decisão confirmada: o binário será obrigatório; o formato JSON somente com
  metadados deixará de ser aceito e deverá resultar em HTTP `400`.
- [x] Decisão confirmada: o fluxo posterior e as falhas nessa etapa são tratados pela
  spec de mensageria de documentos.
- [x] Decisão confirmada: esta feature usará processamento assíncrono com staging
  durável; não haverá migration para essa gravação.
- [x] Decisão confirmada: o limite máximo é documentado diretamente como
  `52428800` bytes, equivalente a 50 MiB.
- [x] Decisão confirmada: a parte binária se chama `file`; `filename` e `Content-Type`
  vêm dos headers da própria parte.
- [x] Decisão confirmada: não haverá parte `metadata`; `filename` e `Content-Type` vêm
  dos headers da parte `file`, e `sizeBytes` é calculado durante a gravação.
- [x] Decisão confirmada: o tamanho será contado pelo `CountingInputStream` do Apache
  Commons IO durante a única leitura do stream para o staging, sem `byte[]` ou arquivo
  temporário da aplicação.
- [x] Decisão confirmada: a validação por assinatura será feita agora com Apache Tika;
  divergência entre a assinatura detectada e o `Content-Type` declarado resultará em
  `400`.
- [x] Decisão confirmada: `TikaInputStream.get(stream)` será usado para que o sniffing
  não consuma o conteúdo que ainda precisa ser gravado, inclusive em streams sem
  `mark/reset`.
- [x] Decisão confirmada: o processamento posterior será assíncrono, com gravação do
  conteúdo em staging durante a requisição e publicação de `contentReference` na
  mensageria.
- [x] Decisão confirmada: o `InputStream` deve ser gravado em storage durável durante a
  requisição HTTP, antes da publicação da mensagem; o `contentReference` será a chave
  do objeto de staging, por exemplo, `staging/{documentId}`.
- [x] Decisão confirmada: a mensagem nunca transportará
  o binário ou o `InputStream`; transportará apenas a referência ao staging.
- [x] Decisão confirmada: o staging usará o cliente/adaptador MinIO já existente, em
  bucket separado do bucket final.
- [x] Decisão confirmada: o componente que grava o staging fechará o `InputStream` com
  try-with-resources assim que a escrita terminar.
- [x] Decisão confirmada: erros `400` usam `application/problem+json` no formato
  `ProblemDetail` do Spring Boot 3.
- [x] Decisão confirmada: o `ProblemDetail` usará a propriedade extensível `errorCode`
  com string estável e, quando aplicável, `invalidField` com o campo inválido.
- [x] Decisão confirmada: o `ProblemDetail` não terá envelope adicional nem outras
  propriedades obrigatórias além de `errorCode` e, quando aplicável, `invalidField`.
- [x] Decisão confirmada: o bucket operacional de staging será `docflow-staging`,
  separado do bucket final, com referência lógica no formato `staging/{documentId}`.
- [x] Decisão confirmada: quando o Apache Tika não identificar uma assinatura específica,
  o sistema tratará o resultado como divergência e rejeitará a requisição com HTTP `400`.

## 9. Definition of Done desta feature

- [ ] Critério de aceite (seção 5) satisfeito
- [ ] Casos de erro de prioridade alta tratados
- [ ] Features existentes continuam funcionando (regressão checada)
- [ ] Testado seguindo `specs/05-verificacao/plano-de-teste-template.md`
- [ ] Eu revisei e aprovei antes do deploy
