# Spec da Feature — Logs operacionais e auditoria

Status: Aprovada  
Projeto: DocFlow  
Onde vive no código: `backend/.../document/` e `frontend/src/app/`, com persistência em PostgreSQL

## 1. Objetivo

Criar uma forma centralizada de registrar e consultar o que acontece no DocFlow: sucessos,
falhas, warnings e eventos relevantes do ciclo de vida dos documentos e dos serviços
associados.

Os eventos relevantes serão publicados de forma assíncrona no RabbitMQ e persistidos por
um serviço lógico de logs. A aplicação também manterá logs estruturados locais como
fallback operacional. O usuário deverá conseguir consultar os registros por uma API e
por uma visualização simples, sem acesso direto ao RabbitMQ ou ao PostgreSQL.

Esta feature é complementar às notificações do Hub, mas tem finalidade operacional e de
auditoria. Um log não é uma notificação de usuário e uma falha ao persistir um log não
pode alterar o fluxo principal do documento.

## 2. Como funciona hoje

- O backend possui fluxos assíncronos de documentos usando RabbitMQ, PostgreSQL e MinIO.
- O domínio controla os estados `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.
- O Hub e as notificações mostram o estado do documento, mas ainda não existe uma visão
  central de eventos técnicos e de negócio.
- O frontend não consulta logs, RabbitMQ, PostgreSQL ou MinIO diretamente.
- Não há autenticação ou associação persistida entre registros de log e usuário.
- A operação atual depende dos logs locais do processo e dos testes para diagnosticar
  o que aconteceu.

## 3. Requisitos funcionais

### FR-001

QUANDO ocorrer um evento relevante no backend  
O SISTEMA DEVE criar um registro estruturado contendo, no mínimo, identificador do
evento, timestamp, nível, serviço, componente, operação e mensagem segura.

### FR-002

QUANDO um documento for aceito, processado, concluído, falhar, entrar em retry, for
marcado para exclusão ou for excluído  
O SISTEMA DEVE registrar o evento correspondente, incluindo `documentId` quando existir.

### FR-003

QUANDO ocorrer um warning operacional ou uma falha de infraestrutura relevante  
O SISTEMA DEVE registrar o evento com nível `WARN` ou `ERROR`, sem alterar o estado do
documento apenas por causa do registro.

### FR-004

QUANDO um evento de log for criado  
O SISTEMA DEVE publicá-lo de forma assíncrona no RabbitMQ para o serviço de persistência,
sem bloquear a resposta principal por uma escrita síncrona de log.

### FR-005

QUANDO o serviço de persistência consumir um evento válido  
O SISTEMA DEVE persistir o registro em PostgreSQL e confirmar a mensagem somente depois
da persistência bem-sucedida ou do tratamento idempotente de um evento já persistido.

### FR-006

QUANDO RabbitMQ ou o serviço de persistência estiver indisponível  
O SISTEMA DEVE manter o log estruturado local como fallback e não deve converter a
indisponibilidade do logging em falha do fluxo principal do documento.

### FR-007

QUANDO um evento de log for entregue mais de uma vez  
O SISTEMA DEVE evitar registros duplicados usando o identificador único do evento.

### FR-008

QUANDO o usuário consultar os logs pela API  
O SISTEMA DEVE retornar registros paginados e permitir filtros por nível, serviço,
`documentId`, intervalo de tempo, operação e identificadores de correlação disponíveis.

### FR-009

QUANDO o usuário abrir a visualização de logs  
O SISTEMA DEVE abrir a rota separada `/logs`, apresentar os registros de forma legível e
permitir distinguir sucessos, warnings e falhas sem expor segredos.

### FR-010

QUANDO um log estiver associado a um documento  
O SISTEMA DEVE permitir localizar o documento relacionado sem recriar, alterar ou excluir
o documento a partir da consulta de logs.

### FR-013

QUANDO um registro persistido ultrapassar três horas desde `occurredAt`  
O SISTEMA DEVE removê-lo automaticamente da persistência de logs, sem alterar documentos
ou outros dados do sistema.

### FR-011

QUANDO uma mensagem de log for inválida ou não puder ser persistida após as tentativas
previstas  
O SISTEMA DEVE encaminhá-la para a DLQ e registrar o motivo no log local/operacional,
sem bloquear o processamento do documento.

### FR-012

QUANDO o sistema registrar qualquer evento  
O SISTEMA NÃO DEVE persistir senha, API key, token, conteúdo binário, credencial ou
segredo em mensagem, contexto ou detalhes do log.

## 4. Regras de negócio

### BR-001

Logs estruturados locais são o fallback operacional. RabbitMQ não é a única fonte de
diagnóstico e uma falha do broker não pode interromper o fluxo de negócio.

### BR-002

Na primeira versão, o sistema registrará todos os eventos e níveis disponíveis no fluxo
de logging da aplicação, sem excluir categorias de baixo nível por decisão funcional.
Sanitização de segredos continua obrigatória.

### BR-003

Os níveis mínimos são `INFO`, `WARN` e `ERROR`. O evento deve identificar o serviço, o
componente e a operação que o produziram.

### BR-004

O registro deve usar `eventId` idempotente. Reentrega não pode gerar duas linhas
equivalentes no armazenamento persistido.

### BR-005

O evento pode carregar `correlationId` e `documentId` quando disponíveis. Ausência de
`documentId` não invalida um log técnico.

### BR-006

Mensagens e detalhes devem ser sanitizados antes de serem publicados. Stack traces e
respostas de provedores externos não podem ser copiados sem filtragem de segredos.

### BR-007

O serviço de persistência de logs não pode alterar `Document`, `DocumentStatus`,
`failure_count`, retry, staging, storage ou exclusão.

### BR-008

A consulta de logs é somente leitura. Nesta primeira versão não haverá edição, exclusão
manual, reprocessamento ou ação operacional disparada pela tela de logs.

### BR-009

Os eventos de log devem permanecer separados dos eventos de notificação ao usuário,
mesmo quando descrevem o mesmo fato.

### BR-010

Sem autenticação, a visualização de logs pertence ao contexto único da instalação local e
não deve ser exposta publicamente sem uma decisão de autorização.

### BR-011

Os registros persistidos terão retenção de três horas, contadas a partir de `occurredAt`.
A limpeza automática deve ser idempotente e não pode afetar documentos, notificações ou
logs locais mais recentes.

## 5. Contrato lógico do evento

O contrato interno deve conter, no mínimo:

```json
{
  "eventId": "00000000-0000-0000-0000-000000000000",
  "schemaVersion": 1,
  "occurredAt": "2026-10-29T12:00:00Z",
  "level": "ERROR",
  "service": "document-api",
  "component": "document-storage-consumer",
  "operation": "document.processing",
  "message": "Falha ao confirmar o storage final",
  "correlationId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "details": {
    "errorCode": "STORAGE_UNAVAILABLE"
  }
}
```

O contrato não deve incluir segredo, conteúdo binário, bucket, API key, token ou stack
trace não sanitizado. O contrato físico de exchange, filas, colunas e DLQ pertence à
ADR da feature e não deve aparecer na interface do usuário.

## 6. Critérios de aceite

### AC-001 — Evento de sucesso

DADO um documento aceito e processado com sucesso  
QUANDO o fluxo persistir o estado `COMPLETED`  
ENTÃO deve existir um evento estruturado de sucesso com `documentId`, operação e
timestamp, disponível para persistência e consulta.

### AC-002 — Evento de falha e warning

DADO que ocorra uma falha de storage ou um warning operacional  
QUANDO o evento for produzido  
ENTÃO o log deve indicar o nível correto, a operação e o contexto seguro sem alterar o
estado do documento por causa do logging.

### AC-003 — Persistência assíncrona

DADO um evento válido publicado no RabbitMQ  
QUANDO o consumidor de logs o processar  
ENTÃO o registro deve ser persistido no PostgreSQL e a mensagem deve ser confirmada após
a persistência.

### AC-004 — Fallback do broker

DADO que RabbitMQ esteja indisponível  
QUANDO a aplicação tentar registrar um evento  
ENTÃO o fluxo principal deve continuar, o log estruturado local deve registrar a falha
de publicação e nenhum documento deve ser marcado como falho por esse motivo.

### AC-005 — Idempotência

DADO que o mesmo evento seja entregue duas vezes  
QUANDO o consumidor processar a segunda entrega  
ENTÃO deve existir somente um registro persistido para o mesmo `eventId`.

### AC-006 — Consulta

DADO que existam eventos persistidos  
QUANDO o usuário consultar `GET /logs` ou abrir a rota `/logs`  
ENTÃO deve conseguir ver os registros paginados e filtrar por nível, serviço,
`documentId`, operação, intervalo de tempo e correlação.

### AC-007 — DLQ

DADO um evento inválido ou uma falha permanente de persistência  
QUANDO as tentativas previstas forem esgotadas  
ENTÃO a mensagem deve chegar à DLQ, o motivo deve ser registrado localmente e o fluxo
de documentos deve continuar independente.

### AC-008 — Sanitização

DADO um evento contendo contexto operacional  
QUANDO ele for publicado ou persistido  
ENTÃO credenciais, tokens, API keys, conteúdo binário e segredos não devem aparecer no
payload ou na consulta.

### AC-009 — Retenção

DADO um registro persistido há mais de três horas  
QUANDO o processo de retenção executar  
ENTÃO o registro deve ser removido da persistência de logs sem alterar documentos ou
outros dados do sistema.

### AC-010 — Captura inicial sem exclusões funcionais

DADO um evento disponível no fluxo de logging da aplicação, inclusive de baixo nível  
QUANDO o sistema processar o evento  
ENTÃO ele deve registrá-lo conforme o contrato, aplicando somente a sanitização de
segredos e não uma lista inicial de categorias excluídas.

## 7. Fora de escopo

- autenticação, autorização e multiusuário;
- alteração de documentos a partir da tela de logs;
- exclusão manual ou edição de logs pela interface;
- observabilidade externa completa com ELK, OpenSearch, Grafana ou SaaS;
- tracing distribuído completo e métricas de negócio avançadas;
- retenção infinita ou histórico imutável para fins legais;
- envio de logs por e-mail ou notificação ao usuário;
- substituição dos logs locais do runtime por RabbitMQ.

## 8. Suposições e perguntas abertas

- [x] Decisão: a visualização será uma rota separada `/logs`.
- [x] Decisão: os registros persistidos terão retenção de três horas.
- [x] Decisão: a consulta terá filtros por nível, serviço, `documentId`, operação,
  intervalo de tempo e correlação, além de paginação.
- [x] Decisão: não haverá lista funcional de exclusões na primeira versão; o sistema
  registrará tudo que estiver disponível no fluxo de logging, mantendo a sanitização de
  segredos.
- [x] Suposição: o primeiro serviço de logs será um componente lógico dentro do backend,
  consumindo RabbitMQ e persistindo PostgreSQL; não será um microserviço implantável
  separado nesta versão.
- [x] Suposição: a visualização inicial será read-only e pertencerá ao contexto único da
  instalação, sem autenticação.
- [x] Suposição: logs locais estruturados serão o fallback quando RabbitMQ estiver
  indisponível.

## 9. Definition of Done da feature

- [ ] A Spec foi revisada e aprovada por Marcos.
- [ ] A ADR de mensageria e persistência de logs foi revisada e aceita.
- [ ] Eventos relevantes possuem contrato versionado e sanitizado.
- [ ] RabbitMQ possui exchange, fila, retry, DLX e DLQ próprios para logs.
- [ ] O consumidor persiste logs com idempotência no PostgreSQL.
- [ ] O fallback local funciona quando RabbitMQ está indisponível.
- [ ] A API e a visualização read-only retornam logs paginados.
- [ ] A rota `/logs` oferece filtros completos e a retenção automática de três horas.
- [ ] A captura inicial não exclui categorias funcionais de log, além da sanitização de
  segredos.
- [ ] Nenhum segredo aparece em logs, payloads, banco ou frontend.
- [ ] Falhas do serviço de logs não alteram o ciclo de vida de documentos.
- [ ] Testes unitários, de integração e regressão foram executados.
- [ ] A convergência `Spec ↔ ADR ↔ Plano/Tarefas ↔ Código ↔ Testes` foi revisada.
- [ ] Marcos aprovou a implementação antes de qualquer deploy.
