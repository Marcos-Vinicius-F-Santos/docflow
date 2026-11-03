# Spec da Feature — Notificações de documentos

Status: Aprovada  
Projeto: DocFlow  
Onde vive no código: `backend/.../document/messaging/` e `frontend/src/app/`, com entrega no Hub

### Emenda de decisão — 2026-10-29

A implementação de e-mail deixa de usar Brevo e passa a usar Web3Forms. A troca fica
restrita ao adaptador atrás da porta provider-neutral; RabbitMQ, retries, DLQ, o
contrato de evento e o isolamento do ciclo de vida permanecem inalterados.

O backend enviará `POST` JSON para `https://api.web3forms.com/submit` usando a
variável `WEB3FORMS_ACCESS_KEY`. O access key da Web3Forms determina a caixa de
destino; `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` continua sendo a configuração
operacional do destinatário e o endereço de reply-to do comando. A chamada continua
no backend para preservar a fila assíncrona, retries e DLQ. A Web3Forms exige
liberação para chamadas server-side em cenários que não sejam suportados pelo
plano/conta; isso é pré-requisito para o envio real local.

## 1. Objetivo

Permitir que o usuário acompanhe, no Hub, os principais eventos do ciclo de vida de
um documento sem precisar atualizar manualmente a página. O usuário deve receber uma
notificação quando o processamento começar, quando terminar com sucesso, quando falhar,
trinta minutos antes da exclusão automática e quando a exclusão for concluída.

O backend continuará usando RabbitMQ para transportar eventos internos. O navegador
receberá as notificações por WebSocket; RabbitMQ e MinIO não serão acessados diretamente
pelo frontend.

Esta feature complementa o Hub de documentos e não altera as transições de estado,
as regras de retry ou a política de exclusão aprovadas nas decisões anteriores.

## 2. Como funciona hoje

- O DocFlow processa documentos de forma assíncrona usando RabbitMQ.
- O documento possui os estados públicos `PENDING`, `PROCESSING`, `COMPLETED` e
  `FAILED`.
- O Hub é a tela de acompanhamento e deve continuar sendo a fonte visual dos
  documentos retornados pela API REST.
- Um documento que chega a três falhas é marcado para exclusão três horas depois,
  conforme a Spec do Hub e a ADR-007.
- O frontend ainda não possui um canal de eventos em tempo real nem uma área de
  notificações.
- Não existe autenticação ou vínculo persistido entre documento e usuário.

## 3. Requisitos funcionais

### FR-001

QUANDO o usuário abrir o Hub  
O SISTEMA DEVE estabelecer uma conexão WebSocket com o backend para receber eventos de
documentos.

### FR-002

QUANDO a conexão WebSocket estiver ativa  
O SISTEMA DEVE manter o navegador fora da infraestrutura interna, recebendo somente o
contrato de notificação exposto pelo backend.

### FR-003

QUANDO o processamento de um documento efetivamente começar, incluindo o processamento
inicial ou um retry aceito  
O SISTEMA DEVE publicar e entregar uma notificação de processamento iniciado contendo,
no mínimo, o identificador do documento, o tipo do evento e o instante de ocorrência.

### FR-004

QUANDO o processamento terminar com sucesso e o documento for confirmado como
`COMPLETED`  
O SISTEMA DEVE publicar e entregar uma notificação de processamento concluído.

### FR-005

QUANDO uma tentativa de processamento terminar em falha efetiva e o documento for
marcado como `FAILED`  
O SISTEMA DEVE publicar e entregar uma notificação de falha.

### FR-006

QUANDO um documento atingir a terceira falha e tiver `exclusion_scheduled_at` definido
para três horas no futuro  
O SISTEMA DEVE agendar uma notificação para trinta minutos antes de
`exclusion_scheduled_at`.

### FR-007

QUANDO chegar o instante de trinta minutos antes da exclusão automática  
O SISTEMA DEVE entregar uma notificação informando que o documento será excluído em
trinta minutos.

### FR-008

QUANDO a exclusão automática ou manual remover o documento conforme a política de
exclusão definitiva  
O SISTEMA DEVE publicar e entregar uma notificação de documento excluído.

### FR-009

QUANDO o Hub receber uma notificação válida  
O SISTEMA DEVE exibir um toast e registrar a mensagem em um banner persistente da
sessão, associando-a ao documento, sem remover a listagem nem substituir a fonte de
verdade REST.

### FR-010

QUANDO o Hub receber uma notificação de documento que já não esteja presente na
listagem atual  
O SISTEMA DEVE atualizar a consulta de documentos antes de oferecer uma ação sobre o
documento, sem recriá-lo no frontend.

### FR-011

QUANDO a conexão WebSocket for interrompida  
O SISTEMA DEVE tentar reconectar automaticamente e, ao restabelecer a conexão, atualizar
a listagem do Hub pela API REST.

### FR-012

QUANDO o Hub receber duas vezes o mesmo evento lógico  
O SISTEMA DEVE evitar a duplicação visual da notificação durante a mesma sessão, usando
o identificador único do evento.

### FR-013

QUANDO o backend não conseguir entregar um evento para uma conexão WebSocket  
O SISTEMA DEVE preservar o estado do documento no fluxo principal e registrar o erro
operacionalmente, sem converter a falha de entrega em falha do processamento do
documento.

### FR-014

QUANDO um dos cinco eventos de ciclo de vida for confirmado pelo fluxo de notificações  
O SISTEMA DEVE encaminhar também uma notificação por e-mail para o destinatário
definido em `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL`, sem depender de uma conexão WebSocket
ativa.

## 4. Regras de negócio

### BR-001

RabbitMQ é o transporte interno dos eventos. O navegador nunca recebe credenciais,
nomes de filas, routing keys, referências de storage ou acesso direto ao broker.

### BR-002

WebSocket é o canal de entrega em tempo real para o Hub. A API REST continua sendo a
fonte de verdade para o estado e para a listagem dos documentos.

### BR-003

O evento de processamento iniciado só pode ser emitido depois que a transição para
`PROCESSING` tiver sido aceita pelo fluxo de aplicação.

### BR-004

O evento de processamento concluído só pode ser emitido depois que o documento estiver
persistido como `COMPLETED` e o conteúdo final tiver sido confirmado.

### BR-005

O evento de falha deve refletir uma falha efetiva de processamento. Falhas de entrega da
notificação não incrementam `failure_count` nem alteram o estado do documento.

### BR-006

O evento de falha deve poder informar o contador lógico `falhas` e a possibilidade de
exclusão, mas não deve expor nomes físicos de colunas ou detalhes de infraestrutura.

### BR-007

O aviso de exclusão só pode ser emitido para documentos marcados para exclusão. O prazo
de referência é `exclusion_scheduled_at`, definido quando `failure_count` chega a três.

### BR-008

O evento de documento excluído só pode ser emitido depois que o conteúdo binário, o
staging e os metadados tiverem sido tratados conforme a política de exclusão definitiva.

### BR-009

Cada evento deve possuir um identificador único, versão de schema, tipo, identificador
do documento e instante de ocorrência. A entrega deve ser tratada como pelo menos uma
vez; consumidores e frontend devem tolerar duplicidade.

### BR-010

O ciclo de vida do documento continua independente da disponibilidade do WebSocket.
Uma desconexão não pausa processamento, retry, exclusão ou reconciliação.

### BR-011

Sem autenticação, a primeira versão poderá entregar eventos do contexto único da
instalação às conexões do Hub ativas. Não haverá filtragem por usuário persistido.

### BR-012

As notificações desta primeira versão são entregues por toast e banner persistente na
sessão, além do canal de e-mail. Não haverá histórico persistido com leitura,
arquivamento ou contador de não lidas no Hub.

### BR-013

Todas as abas ativas do Hub conectadas ao contexto único da instalação devem receber os
eventos WebSocket. A feature não tentará identificar qual aba iniciou o upload.

### BR-014

O envio por e-mail deve ser desacoplado do processamento do documento. Uma falha no
envio de e-mail não pode alterar o status, o contador de falhas, o retry ou a exclusão
do documento.

### BR-015

O endereço em `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` é configuração operacional do
ambiente e não pode ser hardcoded, versionado ou recebido pelo navegador.

### BR-016

O envio de e-mail deve usar o adaptador provider-neutral do backend. A implementação
atual usará Web3Forms por API, com access key em variável de ambiente, podendo ser
substituída sem alterar o contrato de eventos.

A escolha da Web3Forms não substitui RabbitMQ: RabbitMQ continua responsável pelo
transporte interno, desacoplamento, retry e DLQ; Web3Forms é somente o provedor
externo de entrega de e-mail.

### BR-017

Falhas temporárias de e-mail — timeout, indisponibilidade do provedor, erro `5xx` ou
limite `429` — devem ter no máximo três retries após a tentativa inicial, nos atrasos de
30 segundos, 5 minutos e 30 minutos. Depois disso, a mensagem deve ir para a DLQ.

### BR-018

Falhas permanentes — endereço inválido, credencial ausente ou inválida, rejeição de
configuração e erros `4xx` que não sejam `429` — devem ir diretamente para a DLQ. Em
nenhum caso a falha de e-mail deve alterar o documento.

## 5. Contrato lógico de notificação

Os nomes físicos de exchange, filas e routing keys pertencem à ADR da feature e não
devem aparecer no frontend.

O contrato entregue ao Hub deve representar, no mínimo:

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

Campos de estado e contagem devem ser omitidos ou nulos quando não fizerem sentido para
o tipo do evento. O frontend deve depender de `type`, `documentId`, `eventId` e
`occurredAt`, não de nomes internos de banco ou broker.

## 6. Critérios de aceite

### AC-001 — Processamento iniciado

DADO um documento aceito para processamento e uma conexão ativa no Hub  
QUANDO o backend persistir a transição para `PROCESSING`  
ENTÃO o usuário deve receber uma notificação de processamento iniciado associada ao
documento correto.

### AC-002 — Processamento concluído

DADO um documento em processamento  
QUANDO o storage final for confirmado e o documento for persistido como `COMPLETED`  
ENTÃO o usuário deve receber uma notificação de conclusão e o Hub deve exibir o estado
concluído ao sincronizar a listagem.

### AC-003 — Falha de processamento

DADO um documento em processamento  
QUANDO uma falha efetiva fizer o documento chegar a `FAILED`  
ENTÃO o usuário deve receber uma notificação de falha contendo o documento e o contador
de falhas disponível para apresentação.

### AC-004 — Aviso de exclusão

DADO um documento marcado para exclusão com prazo de três horas  
QUANDO faltar trinta minutos para `exclusion_scheduled_at`  
ENTÃO o usuário deve receber uma notificação de aviso de exclusão sem que o documento
seja excluído antecipadamente.

### AC-005 — Exclusão concluída

DADO um documento com exclusão automática ou manual em andamento  
QUANDO conteúdo binário, staging e metadados forem removidos conforme a política
aprovada  
ENTÃO o usuário deve receber uma notificação de documento excluído e o Hub não deve
recriar o item removido.

### AC-006 — Reconexão

DADO que a conexão WebSocket tenha sido interrompida durante o uso do Hub  
QUANDO o navegador restabelecer a conexão  
ENTÃO o Hub deve consultar novamente a API REST e voltar a receber eventos futuros,
sem pausar o ciclo de vida do documento.

### AC-007 — Duplicidade

DADO que o mesmo evento seja entregue duas vezes  
QUANDO o Hub processar a segunda entrega com o mesmo `eventId`  
ENTÃO deve existir somente uma apresentação visual daquela notificação na sessão.

### AC-008 — Falha do canal de notificação

DADO que RabbitMQ ou WebSocket esteja indisponível  
QUANDO o processamento do documento continuar no backend  
ENTÃO o estado do documento não deve ser revertido nem marcado como falho por causa da
notificação, e a próxima sincronização REST deve refletir o estado real.

### AC-009 — Canais de apresentação

DADO um evento válido recebido enquanto o Hub estiver aberto em uma ou mais abas  
QUANDO o frontend processar o evento  
ENTÃO cada aba ativa deve exibir um toast e registrar a notificação em um banner
persistente na sessão, sem duplicar a mesma entrega por `eventId`.

### AC-010 — E-mail

DADO um destinatário de e-mail configurado para a instalação  
QUANDO um evento de ciclo de vida for confirmado pelo fluxo de notificações  
ENTÃO o sistema deve enfileirar e tentar enviar o e-mail correspondente, e uma falha
desse envio não deve alterar o estado do documento.

## 7. Fora de escopo

- autenticação, autorização, multiusuário ou vínculo de notificações a contas;
- push do sistema operacional, SMS ou outro canal externo que não seja o e-mail
  configurado;
- histórico persistido de notificações, marcar como lida ou contador de não lidas;
- envio direto do navegador para RabbitMQ;
- alteração das regras de retry, do contador de falhas ou da política de exclusão;
- pausa ou compensação do processamento quando o WebSocket estiver indisponível;
- configuração de estilos finais, sons ou animações não definidos pelo Hub.

## 8. Suposições e perguntas abertas

- [x] Suposição: “Web3Socket” significa WebSocket.
- [x] Decisão: a interface usará toast temporário e banner persistente na sessão.
- [x] Decisão: todas as abas ativas do Hub receberão cada evento WebSocket; não haverá
  direcionamento somente para a aba que iniciou o upload.
- [x] Suposição: a primeira versão entrega notificações WebSocket somente para conexões
  ativas do Hub; ao reconectar, o estado é recuperado por `GET /documents`, mas
  notificações antigas não serão reproduzidas no WebSocket.
- [x] Suposição: sem login, o contexto é a instalação única do DocFlow e todas as
  conexões ativas do Hub podem receber os eventos dos documentos visíveis pela API.
- [x] Suposição: será usado WebSocket nativo com contrato JSON simples, sem expor
  RabbitMQ ou exigir STOMP no navegador.
- [x] Decisão proposta: o destinatário temporário será definido por
  `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL`, sem valor no repositório.
- [x] Decisão substituída: a primeira integração usaria Brevo por API, atrás de um
  adaptador provider-neutral.
- [x] Decisão proposta: erros temporários terão retries em 30 segundos, 5 minutos e
  30 minutos; depois da tentativa inicial e dos três retries, a mensagem irá para a
  DLQ. Erros permanentes irão diretamente para a DLQ.
- [x] Decisão aprovada em 2026-10-29: a integração atual usará Web3Forms por API,
  atrás do adaptador provider-neutral. O access key determina a caixa de destino e
  essa escolha não altera o uso do RabbitMQ. Chamadas server-side dependem da
  liberação/plano da Web3Forms.

## 9. Definition of Done da feature

- [ ] A Spec foi revisada e aprovada por Marcos.
- [ ] O ADR da combinação RabbitMQ + WebSocket foi revisado e aprovado.
- [ ] Os cinco eventos possuem contrato versionado e testes de publicação/consumo.
- [ ] O Hub conecta, reconecta e sincroniza o estado pela API REST.
- [ ] Eventos duplicados não geram duplicidade visual na mesma sessão.
- [ ] Falhas do canal de notificação não alteram o ciclo de vida do documento.
- [ ] O aviso ocorre trinta minutos antes do prazo persistido de exclusão.
- [ ] A notificação de exclusão só ocorre depois da remoção aprovada dos recursos.
- [ ] RabbitMQ não é acessado diretamente pelo frontend.
- [ ] O caminho principal e as falhas de entrega possuem testes automatizados.
- [ ] O destinatário é carregado exclusivamente por configuração segura e o envio de
  e-mail possui retries e DLQ testados.
- [ ] A convergência `Spec ↔ ADR ↔ Plano/Tarefas ↔ Código ↔ Testes` foi revisada.
- [ ] Marcos aprovou a implementação antes de qualquer deploy.
