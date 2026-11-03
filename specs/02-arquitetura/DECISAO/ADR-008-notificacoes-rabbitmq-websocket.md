# Registro de Decisão — Notificações via RabbitMQ, WebSocket e e-mail

## Emenda 2026-10-29 — Web3Forms

A decisão anterior de usar Brevo como provedor inicial foi substituída. O provedor
externo atual é Web3Forms, consumido pelo adaptador provider-neutral do backend.
RabbitMQ continua sendo o transporte interno, inclusive para retries e DLQ; a
Web3Forms apenas entrega o e-mail.

O adaptador usa `WEB3FORMS_ACCESS_KEY` e o endpoint configurável
`WEB3FORMS_API_URL`, com padrão `https://api.web3forms.com/submit`. O access key
determina o destinatário na Web3Forms e `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` é
mantido como configuração operacional e reply-to. Chamadas server-side podem exigir
plano e liberação do IP pela Web3Forms; sem isso, o envio real pode ser rejeitado
mesmo com um access key válido.

Data: 2026-10-28  
Status: Aceita  
Feature: `notificacoes-documentos`

## Contexto

O Hub precisa informar, em tempo real, que um documento começou a ser processado,
terminou com sucesso, falhou, está a trinta minutos da exclusão automática ou foi
excluído. O processamento e a exclusão já são assíncronos e usam RabbitMQ; o navegador
não deve acessar o broker, o PostgreSQL ou o MinIO diretamente.

Não há autenticação ou usuário persistido nesta fase. Portanto, a solução precisa ser
simples, compatível com o contexto único da instalação e tolerante a conexões de
navegador interrompidas. Também não existe transação distribuída entre PostgreSQL,
RabbitMQ e WebSocket.

## Decisão implementada

### 1. Separação entre transporte interno e entrega ao navegador

RabbitMQ será o transporte interno dos eventos de ciclo de vida. Um consumidor de
notificações traduzirá o evento interno para o contrato JSON do WebSocket.

O WebSocket será o canal de entrega em tempo real para o Hub e o e-mail será um canal
assíncrono adicional. O frontend não receberá credenciais, nomes de filas, routing keys,
referências de storage ou acesso ao broker. A API REST continuará sendo a fonte de
verdade para a listagem e o estado do documento.

O endpoint de entrega proposto é `GET /ws/notifications` por upgrade para WebSocket.
O contrato usa eventos JSON versionados, sem dependência de STOMP no navegador.

### 2. Topologia RabbitMQ

Será criada uma topologia própria, durável e separada da fila de armazenamento de
documentos:

| Elemento | Nome/valor proposto |
|---|---|
| Exchange de eventos | `docflow.document-events` |
| Tipo do exchange | `topic` |
| Fila do serviço de notificações | `docflow.document-notifications.requested` |
| Fila de espera do aviso | `docflow.document-notifications.delay.2h30` |
| Fila de e-mail | `docflow.document-email-notifications.requested` |
| Routing key: comando de e-mail | `notification.email.requested` |
| Fila de retry de e-mail 30s | `docflow.document-email-notifications.retry.30s` |
| Fila de retry de e-mail 5min | `docflow.document-email-notifications.retry.5m` |
| Fila de retry de e-mail 30min | `docflow.document-email-notifications.retry.30m` |
| Routing key: processamento iniciado | `document.processing.started` |
| Routing key: concluído | `document.completed` |
| Routing key: falha | `document.failed` |
| Routing key: aviso agendado | `document.deletion.warning.scheduled` |
| Routing key: aviso vencido | `document.deletion.warning` |
| Routing key: excluído | `document.deleted` |
| Dead-letter exchange | `docflow.document-notifications.dlx` |
| Dead-letter queue | `docflow.document-notifications.dlq` |
| Dead-letter exchange de e-mail | `docflow.document-email-notifications.dlx` |
| Dead-letter queue de e-mail | `docflow.document-email-notifications.dlq` |

O exchange, as filas e as mensagens serão duráveis. A fila de espera do aviso terá TTL
de 2 horas e 30 minutos, encaminhando a mensagem para o exchange principal com a
routing key `document.deletion.warning`. Assim, o aviso chega trinta minutos antes do
prazo de exclusão de três horas definido pela ADR-007.

A fila do serviço de notificações receberá os eventos necessários pelos bindings de
routing key. Para o canal de e-mail, o serviço de notificações publicará comandos
duráveis na fila `docflow.document-email-notifications.requested`; um adaptador de
e-mail consumirá esses comandos e usará a configuração de SMTP/provedor fornecida por
ambiente. A implementação atual usará a API da Web3Forms atrás de um adaptador
provider-neutral. A credencial será carregada por variável de ambiente; nenhum segredo
ou endereço do destinatário será versionado.

Web3Forms não substitui RabbitMQ. RabbitMQ permanece como transporte interno dos
eventos, responsável pelo desacoplamento, retry e DLQ; Web3Forms é somente o provedor
externo de entrega de e-mail atrás do adaptador.

O destinatário temporário será carregado pela variável de ambiente
`DOCFLOW_NOTIFICATION_DEFAULT_EMAIL`. Os detalhes físicos acima são de infraestrutura
e não fazem parte do contrato do frontend.

### 3. Eventos e contrato

Os eventos internos devem conter, no mínimo:

```json
{
  "eventId": "00000000-0000-0000-0000-000000000000",
  "schemaVersion": 1,
  "type": "DOCUMENT_FAILED",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "occurredAt": "2026-10-28T12:00:00Z",
  "status": "FAILED",
  "failureCount": 1,
  "exclusionScheduledAt": null
}
```

O consumidor pode adaptar o evento para o contrato público do WebSocket, mantendo
`eventId`, `schemaVersion`, `type`, `documentId` e `occurredAt`. Nomes físicos de banco,
bucket, exchange, fila e routing key não devem vazar para o navegador.

Os tipos públicos são:

- `DOCUMENT_PROCESSING_STARTED`;
- `DOCUMENT_COMPLETED`;
- `DOCUMENT_FAILED`;
- `DOCUMENT_DELETION_WARNING`;
- `DOCUMENT_DELETED`.

O evento `DOCUMENT_PROCESSING_STARTED` será publicado somente depois da transição
confirmada para `PROCESSING`. `DOCUMENT_COMPLETED` será publicado somente depois da
confirmação do storage final e da persistência do estado. `DOCUMENT_FAILED` será
publicado depois da persistência da falha e do contador correspondente.

O aviso será agendado quando `failure_count` chegar a três e
`exclusion_scheduled_at` for persistido. O evento de exclusão será publicado somente
depois que o consumidor da exclusão automática ou o caso de uso de exclusão manual
concluir o tratamento aprovado pelo ADR-006.

### 4. Confiabilidade e idempotência

Publishers usarão publisher confirms. O consumidor de RabbitMQ confirmará a mensagem
somente depois de encaminhar o evento ao componente de notificações ou de tratar a
mensagem como não entregável conforme a política operacional.

O serviço de notificações não poderá alterar o estado do documento. Uma falha de
entrega WebSocket não poderá incrementar `failure_count`, alterar `FAILED`, reabrir
retry ou desfazer uma exclusão.

O frontend fará deduplicação por `eventId` durante a sessão. O consumidor e o publisher
devem aceitar reentrega de mensagens sem criar novos documentos ou novas exclusões.

Como a primeira versão não terá histórico de notificações WebSocket, se não houver
conexão ativa o serviço poderá confirmar o evento sem reproduzi-lo posteriormente. Ao
reconectar, o Hub consultará `GET /documents` para recuperar o estado atual. O evento de
e-mail seguirá sua própria fila e não dependerá de uma conexão WebSocket ativa.

Falhas temporárias do adaptador de e-mail — timeout, indisponibilidade do provedor,
erro `5xx` ou limite `429` — terão no máximo três retries após a tentativa inicial,
com atrasos de 30 segundos, 5 minutos e 30 minutos. Depois disso, a mensagem será
encaminhada para a DLQ.

Falhas permanentes — endereço inválido, credencial ausente ou inválida, rejeição de
configuração e erros `4xx` que não sejam `429` — serão encaminhadas diretamente para a
DLQ. Em nenhum caso a falha de e-mail alterará o documento.

### 5. Escopo sem autenticação

Enquanto não houver login, o serviço fará broadcast para todas as conexões WebSocket
ativas do contexto único da instalação. Todas as abas abertas receberão cada evento;
não será criado vínculo persistido entre evento, documento, aba ou usuário.

O frontend não deve assumir que uma conexão representa uma conta. Se a aplicação passar
a suportar múltiplos usuários, será necessária nova decisão de autorização e roteamento
por destinatário.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Usar somente RabbitMQ no frontend | O navegador não deve acessar o broker nem conhecer credenciais e topologia interna. |
| Usar somente WebSocket, sem RabbitMQ | Perde a separação entre processamento assíncrono e entrega ao usuário; eventos podem depender da disponibilidade imediata do navegador. |
| Usar polling REST para todas as notificações | É mais simples, mas não atende bem ao requisito de notificação em tempo real e aumenta consultas repetitivas. |
| Usar SSE em vez de WebSocket | SSE atenderia ao fluxo somente servidor → navegador, mas WebSocket foi escolhido como canal explícito para a evolução do Hub e para manter um contrato de conexão bidirecional disponível. |
| Persistir histórico de notificações já nesta versão | Adiciona usuário, leitura, retenção e nova tabela sem requisito confirmado; o estado REST já permite recuperar a situação atual. |
| Publicar notificações diretamente no controller | Não cobre os eventos produzidos por consumidores, reconciliação e exclusão automática e acopla a interface ao ciclo de processamento. |
| Enviar e-mail diretamente no consumidor do documento | Acopla o processamento ao provedor externo e permite que uma falha de e-mail altere indevidamente a percepção do fluxo principal. |
| Usar um provedor SMTP próprio no projeto | Exige operação, reputação de IP, DNS e manutenção incompatíveis com o objetivo de um portfólio gratuito. |

## Consequências

O backend mantém RabbitMQ como infraestrutura interna já aprovada, enquanto o Hub
recebe uma interface simples e desacoplada por WebSocket. O processamento continua
funcionando mesmo sem navegador conectado, e a API REST oferece recuperação do estado
após reconexão.

O custo é manter uma nova topologia, um consumidor de eventos, uma camada de conexão
WebSocket, reconexão no Angular, adaptador de e-mail, configuração segura de SMTP/provedor
e testes de duplicidade/indisponibilidade. Como não há outbox nem histórico WebSocket
nesta primeira versão, notificações transitórias podem ser perdidas em uma
indisponibilidade; a fila de e-mail mantém sua própria entrega assíncrona. Essa
limitação deve ser revisada antes de transformar o fluxo em uma experiência
multiusuário ou auditável.

## Decisões aprovadas

- O destinatário temporário será `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL`, sem valor no
  repositório.
- Web3Forms será o provedor atual por API. A escolha só troca o provedor de e-mail;
  não remove RabbitMQ da arquitetura. Chamadas server-side dependem da liberação/plano
  da Web3Forms.

## Implementação e verificação

Esta decisão está implementada no módulo `document`: os publishers e consumidores
RabbitMQ usam a topologia acima, o endpoint nativo é `/ws/notifications` e o adaptador
Web3Forms mantém o access key somente no backend. O frontend reconecta e consulta
`GET /documents` porque esta versão não possui replay nem histórico persistido.

A evidência automatizada inclui contratos dos cinco eventos, integração RabbitMQ real
com Testcontainers, ACK/NACK, TTL/DLX, retries de e-mail sem rede externa, broadcast
WebSocket, deduplicação por `eventId` e regressão Angular. A referência operacional
detalhada está em [`docs/document-notifications.md`](../../../docs/document-notifications.md).
