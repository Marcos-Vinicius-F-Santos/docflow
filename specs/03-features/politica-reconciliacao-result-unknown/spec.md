# Spec da Feature — Política de reconciliação para resultado desconhecido do storage (`RESULT_UNKNOWN`)

Status: Encerrada — comportamento incorporado em `backend-operacional-docflow`  
Projeto: DocFlow  
Onde vive no código: domínio `document`, respeitando os limites de aplicação, porta `DocumentStorage`, adaptador de storage e mecanismo assíncrono de reconciliação previstos em `specs/02-arquitetura/ARQUITETURA.md`.

## 1. Objetivo

Definir o comportamento do sistema quando uma tentativa de gravação no MinIO retorna
`RESULT_UNKNOWN`. O documento deve permanecer em `PROCESSING` até que a reconciliação
confirme, por meio de `exists(objectKey)`, a gravação do objeto ou até que as tentativas
de reconciliação sejam esgotadas, quando então deve ser marcado como `FAILED`.

Esta feature resolve a inconsistência entre PostgreSQL e MinIO sem converter um resultado
inconclusivo em sucesso.

## 2. Como funciona hoje

Esta spec foi consolidada pela Spec `backend-operacional-docflow`. O comportamento
vigente é um scheduler interno periódico no domínio `document`, com claim atômico,
cinco tentativas nos atrasos de `1m`, `5m`, `15m`, `30m` e `60m`, e configuração
explícita de habilitação. Este arquivo preserva o contrato histórico; a Spec consolidada
é a fonte funcional primária.

## 3. Requisitos funcionais

### FR-001

QUANDO uma tentativa de gravação no storage retornar `RESULT_UNKNOWN`  
O SISTEMA DEVE manter o documento no estado `PROCESSING` e não deve marcá-lo como
`COMPLETED`.

### FR-002

QUANDO o mecanismo de reconciliação verificar `exists(objectKey)` e confirmar que o objeto
foi gravado no storage  
O SISTEMA DEVE mover o documento de `PROCESSING` para `COMPLETED`.

### FR-003

QUANDO uma tentativa de reconciliação não confirmar que o objeto foi gravado e ainda
restarem tentativas  
O SISTEMA DEVE manter o documento em `PROCESSING` para permitir nova tentativa de
reconciliação.

### FR-004

QUANDO as tentativas de reconciliação forem esgotadas sem confirmação de que o objeto foi
gravado  
O SISTEMA DEVE mover o documento de `PROCESSING` para `FAILED`.

### FR-005

QUANDO a política precisar controlar as tentativas de reconciliação de um documento  
O SISTEMA DEVE persistir no PostgreSQL o contador necessário para determinar se as
tentativas foram esgotadas, por meio de uma nova migration Flyway `V4`.

## 4. Regras de negócio

### BR-001

`RESULT_UNKNOWN` não representa confirmação de gravação e não pode resultar diretamente
em `COMPLETED`.

### BR-002

Enquanto houver possibilidade de reconciliação, o documento deve permanecer em
`PROCESSING`.

### BR-003

O documento só pode ser marcado como `COMPLETED` depois que a reconciliação confirmar que
o objeto foi gravado.

### BR-004

Sem confirmação após o esgotamento das tentativas de reconciliação, o documento deve ser
marcado como `FAILED`.

### BR-005

A reconciliação deve apenas verificar o objeto já referenciado por `objectKey`; ela não
deve repetir a gravação do conteúdo.

### BR-006

Para esta feature, a confirmação da gravação é exclusivamente a existência do objeto
verificada por `exists(objectKey)`, implementada pelo adaptador por meio de `statObject`.

## 5. Critério de aceite

### AC-001 — caminho feliz

Dado um documento em `PROCESSING` porque a gravação no storage retornou `RESULT_UNKNOWN`  
Quando o mecanismo de reconciliação verificar `exists(objectKey)` e confirmar que o objeto
foi gravado  
Então o documento deve estar em `COMPLETED`.

### AC-002 — caminho de erro

Dado um documento em `PROCESSING` porque a gravação no storage retornou `RESULT_UNKNOWN`  
Quando as tentativas de reconciliação forem esgotadas sem confirmação de que o objeto foi
gravado  
Então o documento deve estar em `FAILED`.

### AC-003 — resultado desconhecido não convertido em sucesso

Dado um documento cuja tentativa de gravação no storage retornou `RESULT_UNKNOWN`  
Quando nenhuma reconciliação tiver confirmado a existência do objeto por meio de
`exists(objectKey)`  
Então o documento não deve estar em `COMPLETED`.

### AC-004 — tentativa ainda disponível

Dado um documento em `PROCESSING` e uma tentativa de reconciliação que não confirme a
existência do objeto  
Quando ainda houver tentativas disponíveis  
Então o documento deve permanecer em `PROCESSING` e deve poder ser submetido a nova
tentativa de reconciliação.

## 6. Casos de erro / edge cases

- `RESULT_UNKNOWN` na tentativa inicial → o documento permanece em `PROCESSING` e aguarda
  reconciliação.
- `exists(objectKey)` confirmado em uma tentativa posterior → o documento vai para
  `COMPLETED` somente após essa confirmação.
- `exists(objectKey)` não confirmado, mas ainda há tentativas disponíveis → o documento
  continua em `PROCESSING`.
- Nenhuma confirmação até o limite de tentativas → o documento vai para `FAILED`.
- O objeto está presente no MinIO quando o documento chega a `FAILED` → a limpeza por
  `delete(objectKey)` não é executada por esta feature.

## 7. Fora de escopo desta feature

- Alterar o número máximo de tentativas, os atrasos ou o mecanismo de scheduler definidos
  na Spec consolidada.
- Definir o contrato, a topologia e a política de retry do RabbitMQ.
- Repetir a gravação do conteúdo durante a reconciliação; esta feature apenas verifica
  `exists(objectKey)`.
- Confirmar tamanho, `contentType`, checksum ou qualquer evidência além da existência do
  objeto.
- Definir uma nova API para consultar ou disparar a reconciliação.
- Definir o tratamento de `UNAVAILABLE`, `REJECTED` ou outros resultados do storage.
- Executar limpeza de objetos no MinIO após o documento chegar a `FAILED`; o adaptador já
  possui `delete(objectKey)`, mas nada o aciona nesta feature.
- Implementar o fluxo completo de recebimento e gravação do conteúdo binário.

## 8. Suposições e perguntas abertas

- [x] Decisão consolidada: “esgotar tentativas” significa completar cinco tentativas nos
  atrasos definidos pela Spec `backend-operacional-docflow`.
- [x] Decisão confirmada: a reconciliação apenas verifica o objeto já referenciado por
  `objectKey`; ela não repete a gravação.
- [x] Decisão confirmada: a única evidência de confirmação nesta feature é a existência
  retornada por `exists(objectKey)` via `statObject`. Checagens adicionais exigiriam uma
  extensão da porta e ficam fora do escopo.
- [x] Decisão confirmada: `delete(objectKey)` não é acionado quando o documento chega a
  `FAILED`; a política de limpeza permanece fora desta feature.
- [x] Decisão confirmada: o contador das tentativas deve ser persistido no PostgreSQL por
  meio da migration `V4`.
- [x] Decisão consolidada: o scheduler interno periódico dispara a reconciliação; um
  comando administrativo pode existir apenas como fallback operacional.
- [x] Decisão consolidada: o contador e a próxima elegibilidade ficam na tabela
  `documents`, conforme ADR-005 e migrations V4/V5.

## 9. Definition of Done desta feature

- [ ] Critério de aceite (seção 5) satisfeito
- [ ] Casos de erro de prioridade alta tratados
- [ ] Features existentes continuam funcionando (regressão checada)
- [ ] Testado seguindo `specs/05-verificacao/plano-de-teste-template.md`
- [ ] Eu revisei e aprovei antes do deploy
