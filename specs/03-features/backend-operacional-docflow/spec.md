# Spec da Feature — Completar e estabilizar o backend operacional do DocFlow

Status: Aprovada  
Projeto: DocFlow  
Onde vive no código: backend do domínio `document`, infraestrutura local em `infra/`, testes de integração e documentação/specs do projeto.

## 1. Objetivo

Completar e estabilizar o backend operacional já existente do DocFlow, tornando os
testes isolados e reproduzíveis, o ambiente local executável por uma pessoa nova e o
fluxo assíncrono verificável em RabbitMQ, incluindo retries, DLQ e concorrência.

Esta feature também finaliza o tratamento operacional de `RESULT_UNKNOWN`, impede que
falhas permanentes deixem documentos presos em `PROCESSING`, aplica o timeout real de
processamento e alinha as specs e a documentação ao comportamento efetivamente
implementado.

## 2. Como funciona hoje

> As subseções abaixo registram o baseline que motivou esta feature. O comportamento
> vigente após sua implementação está consolidado nas seções 3 a 7 e nos documentos
> oficiais apontados na seção 8.

### Testes e isolamento

- Testes unitários usam mocks e não dependem de PostgreSQL, RabbitMQ ou MinIO.
- `IntegrationTestBase` cria um PostgreSQL por Testcontainers e o reutiliza nos testes
  de integração; a limpeza é feita manualmente, principalmente com `deleteAll()`.
- `DocumentRegistrationEndToEndTest` cria uma stack própria com PostgreSQL, RabbitMQ,
  MinIO e os buckets de staging e final.
- Os demais testes de contexto não criam RabbitMQ nem MinIO, mas tentam acessar o
  RabbitMQ externo em `localhost:5672`, causando os sete erros atuais.
- O MinIO é testado principalmente com mocks, exceto no teste ponta a ponta.

### Ambiente local

O ambiente é definido em `infra/docker-compose.yml` com PostgreSQL, RabbitMQ e MinIO,
e depende de `infra/.env`. A reprodução a partir de `infra/.env.example` ainda falha
ou exige preparação manual porque:

- `RABBITMQ_PASSWORD` está vazio no exemplo, embora seja obrigatório no Compose;
- as credenciais usadas pela aplicação MinIO podem ficar vazias;
- `MINIO_ROOT_USER` e `MINIO_ROOT_PASSWORD` configuram o servidor, mas não configuram
  automaticamente as credenciais usadas pela aplicação;
- os buckets `docflow-staging` e `docflow-documents` não são criados automaticamente;
- volumes persistentes podem manter credenciais antigas do RabbitMQ;
- não existe bootstrap que valide conexões e crie os buckets de forma idempotente.

### `RESULT_UNKNOWN`

O adaptador MinIO produz `RESULT_UNKNOWN` quando existe uma falha inconclusiva de
comunicação, principalmente `IOException`. Isso pode ocorrer ao gravar ou ler staging,
gravar o storage final, consultar `exists(objectKey)` ou remover objetos.

No fluxo final, o documento passa de `PENDING` para `PROCESSING`, recebe
`RESULT_UNKNOWN`, permanece em `PROCESSING` e a mensagem segue para retry RabbitMQ.
Depois do limite de retries, a mensagem vai para a DLQ, mas o documento pode continuar
em `PROCESSING`.

O caso de uso de reconciliação já consegue consultar `exists(objectKey)`, mover para
`COMPLETED` quando o objeto existe, manter `PROCESSING` enquanto ainda houver tentativas
e mover para `FAILED` quando o limite for atingido. Porém, não existe scheduler,
consumer, endpoint ou outro disparador operacional para executar esse caso de uso.

Durante a gravação do staging, um `RESULT_UNKNOWN` ocorre antes da persistência do
documento; o registro falha e pode deixar um objeto órfão no staging.

### Falhas e máquina de estados

O comportamento atual classifica as falhas assim:

| Falha | Comportamento atual |
|---|---|
| `UNAVAILABLE` | Retry |
| `RESULT_UNKNOWN` | Retry |
| `REJECTED` | DLQ, sem retry |
| Mensagem inválida | DLQ direta |
| Falha de validação HTTP | HTTP 400 |
| Falha de publicação RabbitMQ | Documento permanece `PENDING` |
| Falha permanente após claim | Documento pode permanecer `PROCESSING` |

As transições de domínio existentes são `PENDING → PROCESSING`,
`PROCESSING → COMPLETED`, `PENDING → FAILED` e `PROCESSING → FAILED`. O consumidor
atual não chama `markFailed()` em todos os caminhos definitivos, especialmente para
`REJECTED`, retries esgotados, staging indisponível ou mensagem enviada
definitivamente à DLQ.

### Timeout e lease

Existe a constante de três segundos `DocumentStorageRabbitTopology.PROCESSING_TIMEOUT`,
mas ela é usada somente para confirmação de publicação no RabbitMQ e confirmação de
mensagens de retry. Ela não limita o processamento total do consumidor: não há deadline
real, cancelamento, watchdog ou teste específico de timeout.

Existe também uma lease de dez segundos no banco: um documento em `PROCESSING` com
`updated_at` vencido pode ser reivindicado novamente. Se o consumidor morrer, outro
processo pode assumir depois de dez segundos; se ele continuar vivo e travado, não há
ação automática após três segundos.

Quando a confirmação da publicação expira, o documento permanece `PENDING`, o staging
é preservado e a exceção é retornada à API.

### RabbitMQ e concorrência

A topologia atual possui:

- exchange durável `docflow.document-storage`, do tipo `direct`;
- fila principal `docflow.document-storage.requested`;
- routing key `document.storage.requested`;
- exchange de dead-letter `docflow.document-storage.dlx`;
- fila `docflow.document-storage.dlq`;
- filas de retry `docflow.document-storage.retry.5s`,
  `docflow.document-storage.retry.15s` e
  `docflow.document-storage.retry.60s`, com TTL e retorno à fila principal.

O consumidor usa acknowledgment manual. Sucesso usa `basicAck`; mensagem inválida e
falha permanente usam `basicReject(..., false)`; falha transitória publica uma nova
mensagem na fila de retry e só confirma a original depois da confirmação da publicação.
Se a publicação do retry falhar, usa `basicNack(..., true)` para devolver a mensagem à
fila principal.

A aplicação não configura explicitamente número de consumidores, prefetch, limite de
concorrência ou timeout do listener; o comportamento depende dos defaults do Spring
AMQP.

As specs atuais também divergem do comportamento implementado: produto e arquitetura
tratam RabbitMQ, reconciliação e parte do processamento assíncrono como evolução futura,
embora o código, os testes e as configurações atuais já contenham esse fluxo.

## 3. Requisitos funcionais

### FR-001 — Isolamento dos testes

QUANDO a suíte de testes for executada em uma máquina sem RabbitMQ, MinIO ou PostgreSQL
externos previamente configurados  
O SISTEMA DEVE executar testes unitários sem conexão externa e testes de integração com
dependências gerenciadas pela própria suíte, sem tentar usar silenciosamente serviços em
`localhost`.

### FR-002 — Reprodutibilidade do ambiente local

QUANDO uma pessoa iniciar o projeto a partir de um checkout limpo, copiar
`infra/.env.example` e seguir a documentação local  
O SISTEMA DEVE disponibilizar PostgreSQL, RabbitMQ e MinIO com configurações coerentes,
por meio de um serviço de bootstrap separado no Docker Compose. Esse serviço deve
aguardar e validar as conexões necessárias e criar de forma idempotente os buckets
`docflow-staging` e `docflow-documents`, sem exigir edição manual de credenciais vazias
ou criação manual dos buckets.

### FR-003 — Reconciliação operacional de `RESULT_UNKNOWN`

QUANDO um documento estiver em `PROCESSING` por causa de `RESULT_UNKNOWN`  
O SISTEMA DEVE executar a reconciliação por meio de um scheduler interno periódico que
consulte documentos elegíveis, obtenha um claim atômico por documento e consulte
`exists(objectKey)` sem repetir a gravação do conteúdo.

O sistema PODE disponibilizar um comando administrativo como fallback operacional, mas
ele não substitui o scheduler como mecanismo principal.

### FR-004 — Resultado da reconciliação

QUANDO a reconciliação de um documento em `PROCESSING` confirmar que `objectKey` existe  
O SISTEMA DEVE mover o documento para `COMPLETED`.

QUANDO a reconciliação não confirmar a existência de `objectKey` e ainda houver tentativas
disponíveis  
O SISTEMA DEVE manter o documento em `PROCESSING`, agendar a próxima tentativa conforme
o backoff definido e liberar o claim.

QUANDO as tentativas de reconciliação forem esgotadas sem confirmação  
O SISTEMA DEVE mover o documento de `PROCESSING` para `FAILED`.

O limite é de cinco tentativas, com atrasos de `1 minuto`, `5 minutos`, `15 minutos`,
`30 minutos` e `60 minutos`. A elegibilidade da próxima tentativa deve ser persistida
para que o scheduler possa retomá-la após reinício da aplicação.

### FR-005 — Transições para falhas permanentes

QUANDO uma mensagem válida identificar um documento cujo processamento terminou em
falha permanente, incluindo `REJECTED` ou uma falha transitória cujo retry definitivo
tenha sido esgotado  
O SISTEMA DEVE persistir a transição válida para `FAILED` antes de concluir
definitivamente o tratamento da mensagem, impedindo que o documento permaneça preso em
`PROCESSING`.

QUANDO o processamento terminar em `RESULT_UNKNOWN`, inclusive por timeout do cliente
MinIO, o resultado NÃO DEVE ser tratado como falha permanente: o documento deve
permanecer em `PROCESSING` para retry RabbitMQ e posterior reconciliação.

QUANDO uma mensagem for inválida a ponto de não permitir identificar com segurança um
documento  
O SISTEMA DEVE encaminhá-la diretamente para a DLQ sem alterar o estado de nenhum
documento.

QUANDO a publicação inicial no RabbitMQ não for confirmada  
O SISTEMA DEVE manter o documento em `PENDING`, preservar o staging e informar a falha
à API, mantendo o comportamento atual desse caminho.

### FR-006 — Timeout real do processamento

QUANDO um consumidor iniciar uma entrega  
O SISTEMA DEVE aplicar ao processamento completo da entrega o timeout operacional
de três segundos, configurando timeouts de conexão, leitura e escrita no cliente MinIO,
e não somente às confirmações de publicação.

QUANDO o processamento exceder esse timeout  
O SISTEMA DEVE tratar o resultado como `RESULT_UNKNOWN`, impedir a confirmação da
entrega como sucesso, preservar o documento em `PROCESSING` e encaminhar o caso para a
política de retry/reconciliação, sem depender de interrupção forçada de thread.

### FR-007 — Contrato operacional de retries e DLQ

QUANDO ocorrer uma falha transitória no consumo  
O SISTEMA DEVE manter o comportamento de entrega inicial mais três retries, usando as
filas de atraso de `5s`, `15s` e `60s`, e encaminhar a mensagem à DLQ após o limite,
sem `ack` prematuro.

QUANDO ocorrer uma mensagem inválida ou falha permanente  
O SISTEMA DEVE encaminhar a mensagem diretamente para a DLQ, sem retry.

QUANDO a publicação de uma mensagem de retry falhar  
O SISTEMA DEVE devolver a mensagem original à fila principal por `nack` com requeue,
sem confirmar o consumo como concluído.

### FR-008 — Validação real da mensageria

QUANDO os testes de integração da mensageria forem executados  
O SISTEMA DEVE validar contra uma instância real de RabbitMQ a exchange, a fila
principal, as filas de retry, a DLX, a DLQ, os TTLs, a routing key e a ordem de
`ack`/`nack` definida para o fluxo.

### FR-009 — Concorrência explícita e idempotente

QUANDO duas ou mais entregas da mesma solicitação forem processadas concorrentemente  
O SISTEMA DEVE aplicar a política explícita de um consumidor, `prefetch=1`, concorrência
mínima `1` e concorrência máxima `1`, além de lease/claim atômico, de modo que apenas
um processamento efetivo grave o storage final e as demais entregas não dupliquem a
gravação nem removam o staging prematuramente. Esses valores devem ser declarados no
código/configuração da aplicação, sem depender dos defaults do Spring AMQP.

### FR-010 — Teste real de concorrência

QUANDO um teste de integração publicar entregas duplicadas para o mesmo documento e
executar consumidores concorrentes  
O SISTEMA DEVE demonstrar que o claim do documento é exclusivo, que o storage final
não recebe gravação duplicada e que o estado final do documento é consistente.

### FR-011 — Alinhamento de specs e documentação

QUANDO esta feature for implementada e validada  
O SISTEMA DEVE atualizar a Spec do Produto, a Arquitetura, as specs de features
relacionadas e a documentação operacional local para refletir o comportamento real de
RabbitMQ, retries, DLQ, `RESULT_UNKNOWN`, estados, timeout, concorrência e bootstrap,
sem manter como “futuro” um comportamento já entregue.

As fontes oficiais são:

- comportamento funcional: a Spec da Feature aprovada;
- arquitetura e limites: `specs/02-arquitetura/ARQUITETURA.md` e os ADRs;
- contrato HTTP: `docs/api.md`;
- operação local: `docs/document-registration-operations.md`;
- banco e migrations: `docs/database.md`;
- execução geral: `README.md`;
- deploy e rollback: `specs/06-deploy/`;
- validação: `specs/05-verificacao/`, como evidência e não como fonte primária de
  comportamento.

## 4. Regras de negócio

### BR-001

`RESULT_UNKNOWN` nunca representa confirmação de gravação e não pode mover diretamente
um documento para `COMPLETED`.

### BR-002

Enquanto houver tentativa de reconciliação disponível, o documento deve permanecer em
`PROCESSING`.

### BR-003

Depois de confirmado o esgotamento das tentativas sem evidência de armazenamento, o
documento deve estar em `FAILED` e não deve continuar sendo tratado como processamento
ativo.

### BR-004

Uma falha permanente não deve ser retentada. Para documento identificável, sua transição
para `FAILED` deve ocorrer antes do encerramento definitivo da mensagem.

`RESULT_UNKNOWN` não é falha permanente. Depois do limite de retries RabbitMQ, a
mensagem pode ir para a DLQ, mas o documento deve permanecer em `PROCESSING` até a
reconciliação atingir `COMPLETED` ou esgotar suas cinco tentativas e atingir `FAILED`.

### BR-005

Uma mensagem sem correlação confiável com um documento não pode alterar estados do
domínio; deve seguir diretamente para a DLQ.

### BR-006

Falha na publicação inicial mantém o documento em `PENDING`, porque o processamento
assíncrono não foi confirmado como aceito.

### BR-007

O estado do documento só pode ser alterado por transições protegidas do domínio e seus
limites transacionais; consumidores não devem editar o estado diretamente fora dos
métodos de negócio.

### BR-008

PostgreSQL, RabbitMQ e MinIO não formam uma transação distribuída. O sistema deve
preservar estados parciais explicitamente e não transformar resultado inconclusivo em
sucesso.

### BR-009

O staging não deve ser removido durante `RESULT_UNKNOWN`, retry pendente ou falha de
armazenamento final. A política de limpeza posterior não faz parte desta feature.

### BR-010

Autenticação, frontend, multi-tenant e novos fluxos de negócio não fazem parte deste
contrato operacional.

### BR-011

O scheduler só pode iniciar uma reconciliação depois de obter claim atômico do documento
e deve liberar o claim ao terminar a tentativa, inclusive em caso de erro.

### BR-012

Os atrasos entre tentativas de reconciliação são, em ordem, `1 minuto`, `5 minutos`,
`15 minutos`, `30 minutos` e `60 minutos`. A quinta tentativa é a última antes da
transição para `FAILED` quando `exists(objectKey)` não confirmar o objeto.

## 5. Critério de aceite

### AC-001 — caminho feliz: ambiente e fluxo assíncrono

Dado um checkout limpo do projeto, sem serviços externos previamente configurados  
Quando a pessoa copiar `infra/.env.example`, iniciar o ambiente local conforme a
documentação e registrar um documento válido  
Então PostgreSQL, RabbitMQ e MinIO devem ficar disponíveis, os buckets necessários devem
ser criados, o staging deve ser publicado na mensagem, o consumidor deve processar a
solicitação e o documento deve terminar em `COMPLETED` sem intervenção manual adicional.

### AC-002 — caminho feliz: reconciliação de `RESULT_UNKNOWN`

Dado um documento em `PROCESSING` cuja gravação final retornou `RESULT_UNKNOWN` e cujo
objeto existe no storage  
Quando o mecanismo operacional disparar a reconciliação  
Então o sistema deve confirmar `exists(objectKey)`, mover o documento para `COMPLETED` e
não repetir a gravação do conteúdo.

### AC-003 — caminho de erro: falha permanente sem documento preso

Dado um documento em `PROCESSING` associado a uma mensagem válida  
Quando o storage retornar `REJECTED` ou uma falha transitória definitivamente não
retryable tiver seus retries permitidos esgotados  
Então o sistema deve mover o documento para `FAILED`, encaminhar a mensagem à DLQ sem
novo retry e não deixar o documento indefinidamente em `PROCESSING`.

### AC-004 — caminho de erro: reconciliação esgotada

Dado um documento em `PROCESSING` por `RESULT_UNKNOWN` e sem confirmação de
`exists(objectKey)`  
Quando o scheduler executar as cinco tentativas nos atrasos de `1m`, `5m`, `15m`, `30m`
e `60m` sem confirmação  
Então o sistema deve mover o documento para `FAILED` e não deve marcá-lo como
`COMPLETED`.

### AC-005 — timeout real

Dado um consumidor processando uma entrega por mais tempo que o timeout operacional de
três segundos  
Quando a deadline da entrega for excedida  
Então o cliente MinIO deve produzir `RESULT_UNKNOWN`, o consumidor não deve confirmar a
mensagem como sucesso, o documento deve permanecer em `PROCESSING` e o caso deve seguir
retry RabbitMQ e posterior reconciliação.

### AC-006 — retries, DLQ e ack/nack reais

Dado um teste de integração usando RabbitMQ real e uma falha transitória reproduzível  
Quando o consumidor processar a entrega inicial e as tentativas subsequentes  
Então a mensagem deve passar pelas filas de retry de `5s`, `15s` e `60s`, ser enviada
à DLQ após o limite e somente ser confirmada quando o fluxo correspondente tiver sido
concluído conforme a política de ack/nack.

### AC-007 — concorrência real

Dado um documento com duas mensagens duplicadas disponíveis para consumidores
concorrentes  
Quando ambos tentarem reivindicar e processar o documento  
Então somente um deve obter o claim efetivo, o storage final não deve receber gravação
duplicada, o staging não deve ser removido pelo consumidor perdedor e o documento deve
terminar em um estado consistente.

Além disso, a execução deve usar um consumidor, `prefetch=1` e concorrência mínima e
máxima iguais a `1`.

### AC-008 — isolamento dos testes

Dado um ambiente sem RabbitMQ e MinIO externos e sem configuração manual de
`localhost:5672`  
Quando a suíte unitária e os testes de contexto forem executados  
Então eles não devem tentar conectar-se a serviços externos; os testes que exigem
RabbitMQ, PostgreSQL ou MinIO devem iniciar e encerrar suas dependências de teste de
forma controlada, sem os sete erros atuais de contexto.

### AC-009 — documentação coerente

Dado que a implementação e os testes desta feature foram concluídos  
Quando uma pessoa consultar as specs do produto, arquitetura, features relacionadas e
o guia de execução local  
Então os documentos devem descrever o mesmo comportamento observado no código e nos
testes, incluindo o que permanece fora de escopo.

## 6. Casos de erro / edge cases

- `RABBITMQ_PASSWORD` ausente ou vazio no ambiente local → falhar com mensagem de
  configuração acionável antes de iniciar o fluxo, e documentar a configuração correta.
- Credenciais do servidor MinIO diferentes das credenciais da aplicação → o bootstrap
  deve detectar a falha de conexão e informar a configuração necessária.
- Buckets já existentes → o bootstrap deve tratá-los de forma idempotente.
- Volumes persistentes com credenciais antigas → a documentação deve explicar a causa e
  o procedimento operacional seguro para corrigir o ambiente; não apagar volumes
  automaticamente.
- Falha na gravação do staging antes da persistência → informar a falha e registrar a
  possibilidade de objeto órfão; limpeza automática não faz parte desta feature.
- Falha na publicação inicial → manter `PENDING`, preservar staging e retornar erro à
  API.
- Falha na publicação de retry → `nack` com requeue da mensagem original.
- Mensagem sem `documentId` válido → DLQ direta, sem alteração de estado.
- `REJECTED` → DLQ direta e `FAILED` quando houver documento identificável.
- `RESULT_UNKNOWN` no consumidor, inclusive por timeout MinIO → sem `ACK`, retry
  RabbitMQ e, após a DLQ, reconciliação pelo scheduler mantendo `PROCESSING` enquanto
  houver tentativa; após a quinta tentativa, `FAILED`.
- `exists(objectKey)` verdadeiro durante a reconciliação → `COMPLETED`, sem repetir
  `store`.
- Scheduler concorrente ou execução duplicada → claim atômico; somente uma tentativa
  pode consultar o documento por vez.
- Tentativas de reconciliação → atrasos de `1m`, `5m`, `15m`, `30m` e `60m`, com cinco
  tentativas no total.
- Entrega duplicada de documento `COMPLETED` ou `FAILED` → não repetir a gravação; o
  tratamento de ack deve seguir a política de idempotência documentada.
- Consumidor interrompido após adquirir claim → permitir takeover somente após a lease
  vigente expirar.
- Timeout excedido → cliente MinIO usa timeouts de conexão, leitura e escrita de três
  segundos; o fluxo produz `RESULT_UNKNOWN` sem depender de interrupção forçada de
  thread.
- Testes executados em paralelo → não compartilhar estado persistente ou filas de modo a
  produzir dependência entre casos.

## 7. Fora de escopo desta feature

- Autenticação ou autorização.
- Frontend Angular.
- Multi-tenant.
- Novos fluxos de negócio além do registro, processamento, acompanhamento e consulta
  de documentos já existentes.
- Deploy ou hospedagem externa.
- Transação distribuída entre PostgreSQL, RabbitMQ e MinIO.
- Outbox, reprocessamento manual abrangente ou novo mecanismo de compensação além do
  necessário para o comportamento descrito.
- Limpeza automática de objetos órfãos de staging ou de storage final após `FAILED`.
- Alteração do contrato público do `POST /documents` ou do `GET /documents/{id}`, salvo
  quando uma atualização documental for necessária para refletir comportamento já
  implementado.
- Métricas, tracing distribuído ou observabilidade avançada não necessários para provar
  os critérios desta feature.

## 8. Suposições e perguntas abertas

- [x] Decisão: a reconciliação será disparada por scheduler interno periódico, consultando
  documentos `PROCESSING` elegíveis com claim atômico. Um comando administrativo pode
  existir como fallback, mas não é o mecanismo principal.
- [x] Decisão: serão feitas cinco tentativas de reconciliação, com atrasos de `1m`, `5m`,
  `15m`, `30m` e `60m`; a quinta tentativa sem confirmação envia o documento para
  `FAILED`.
- [x] Decisão: RabbitMQ usará um consumidor, `prefetch=1`, concorrência mínima `1` e
  concorrência máxima `1`, declarados explicitamente no código/configuração.
- [x] Decisão: o timeout MinIO será de três segundos para conexão, leitura e escrita.
  Excedê-lo produz `RESULT_UNKNOWN`, sem `ACK`, preservando `PROCESSING` para
  retry/reconciliação e sem interrupção forçada de thread.
- [x] Decisão: o bootstrap será um serviço separado no Docker Compose, idempotente,
  aguardando PostgreSQL, RabbitMQ e MinIO, validando conexões, criando os dois buckets e
  falhando explicitamente quando houver credencial ou serviço incorreto.
- [x] Decisão: a contagem, a próxima data elegível e o estado necessário da
  reconciliação serão persistidos por migration Flyway ou mecanismo equivalente
  versionado.
- [x] Decisão: a reconciliação verifica somente `exists(objectKey)` e não repete `store`.
- [x] Decisão: as fontes oficiais de documentação são as listadas em FR-011; a pasta
  `specs/05-verificacao/` serve como evidência de validação, não como fonte primária de
  comportamento.

## 9. Definition of Done desta feature

- [ ] Critérios de aceite da seção 5 satisfeitos.
- [ ] Isolamento dos testes corrigido e os sete erros atuais eliminados.
- [ ] Ambiente local reproduzível a partir de checkout limpo e `.env.example`.
- [ ] Bootstrap separado no Compose valida PostgreSQL/RabbitMQ/MinIO e cria os buckets
  necessários de forma idempotente.
- [ ] Reconciliação de `RESULT_UNKNOWN` possui disparo operacional, limite persistido,
  caminho para `COMPLETED` e caminho para `FAILED`.
- [ ] Falhas permanentes e retries esgotados não deixam documentos presos em
  `PROCESSING`.
- [ ] Timeout de conexão, leitura e escrita do MinIO é de três segundos e o resultado é
  tratado como `RESULT_UNKNOWN`, sem `ACK` e sem interrupção forçada de thread.
- [ ] RabbitMQ real foi validado para topologia, retries, DLQ, ack/nack e concorrência.
- [ ] RabbitMQ usa um consumidor, `prefetch=1`, concorrência mínima `1` e máxima `1`,
  declarados explicitamente.
- [ ] Scheduler de reconciliação usa claim atômico, cinco tentativas e os atrasos
  definidos.
- [ ] Teste de concorrência confirma claim exclusivo e ausência de gravação duplicada.
- [ ] Specs do produto, arquitetura, features relacionadas e documentação local estão
  alinhadas ao código e aos testes.
- [ ] Regressão mínima do backend executada.
- [ ] `SPEC DA FEATURE ↔ PLANO/TAREFAS ↔ CÓDIGO ↔ TESTE` revisado antes de reportar a
  implementação como concluída.
- [ ] Marcos revisou e aprovou antes de qualquer deploy.
