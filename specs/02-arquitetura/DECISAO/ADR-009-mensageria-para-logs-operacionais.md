# Registro de Decisão — Mensageria para logs operacionais e auditoria

Data: 2026-10-29  
Status: Aceita  
Feature: `logs-operacionais`

## Contexto

O DocFlow possui fluxos assíncronos entre API, PostgreSQL, RabbitMQ e MinIO. O estado
final do documento permite saber o resultado, mas não explica de forma centralizada tudo
que ocorreu: sucessos, warnings, falhas, retries, mensagens, exclusões e problemas de
infraestrutura.

É necessário consultar esses eventos sem acoplar o fluxo principal a uma escrita
síncrona de log. Ao mesmo tempo, usar somente RabbitMQ seria frágil: uma indisponibilidade
do broker justamente durante uma falha poderia eliminar a evidência operacional.

Não há autenticação ou multiusuário nesta fase. Portanto, a solução deve ser adequada a
uma instalação local/única, não expor segredos e não transformar o histórico de logs em
um novo domínio de negócio de documentos.

## Decisão proposta

### 1. Dois caminhos complementares

O sistema terá dois caminhos de registro:

1. Logs estruturados locais em JSON, enviados ao stdout/stderr ou ao logger padrão do
   processo. Esse caminho é obrigatório e funciona como fallback operacional.
2. Eventos relevantes de aplicação publicados de forma assíncrona no RabbitMQ. Um
   consumidor lógico de logs persiste esses eventos em PostgreSQL para consulta.

RabbitMQ não substituirá logging local. Na primeira versão, todos os eventos e níveis
disponíveis no fluxo de logging da aplicação poderão ser encaminhados, inclusive os de
baixo nível; não haverá lista funcional de categorias excluídas. A sanitização de
segredos continua obrigatória.

### 2. Serviço lógico, não microserviço separado

Na primeira versão, o “serviço de logs” será um componente lógico dentro do backend
existente, organizado por domínio/feature. Ele terá publisher, consumidor, persistência e
API de consulta separados por responsabilidade, mas será executado junto da aplicação.

Uma extração para um processo independente só será considerada após volume, retenção,
autorização ou escala justificarem o custo operacional.

### 3. Contrato do evento

O evento terá schema versionado e os campos:

- `eventId` único e idempotente;
- `schemaVersion`;
- `occurredAt`;
- `level`: `INFO`, `WARN` ou `ERROR`;
- `service`, `component` e `operation`;
- `message` sanitizada;
- `correlationId` quando disponível;
- `documentId` quando aplicável;
- `details` estruturado e sanitizado.

Não serão publicados senha, API key, token, conteúdo binário, credencial, bucket ou
stack trace não filtrado. O consumidor poderá rejeitar eventos que violem o contrato e
encaminhá-los para a DLQ.

### 4. Topologia RabbitMQ proposta

Será usada uma topologia separada das notificações do usuário e do armazenamento de
documentos:

| Elemento | Nome/valor proposto |
|---|---|
| Exchange de logs | `docflow.application-logs` |
| Tipo do exchange | `topic` |
| Fila de persistência | `docflow.application-logs.persist` |
| Routing key INFO | `application.log.info` |
| Routing key WARN | `application.log.warn` |
| Routing key ERROR | `application.log.error` |
| Dead-letter exchange | `docflow.application-logs.dlx` |
| Dead-letter queue | `docflow.application-logs.dlq` |
| Retry 5s | `docflow.application-logs.retry.5s` |
| Retry 15s | `docflow.application-logs.retry.15s` |
| Retry 60s | `docflow.application-logs.retry.60s` |

Exchange, filas e mensagens serão duráveis. Falhas temporárias de persistência terão o
mesmo padrão simples de retries do fluxo RabbitMQ existente: 5 segundos, 15 segundos e
60 segundos. Depois da tentativa inicial e dos retries previstos, a mensagem irá para a
DLQ. Eventos inválidos ou erros permanentes de schema/persistência irão para a DLQ sem
requeue infinito.

Publishers usarão publisher confirm. O consumidor só fará ACK depois da persistência
confirmada ou da detecção idempotente de um `eventId` já persistido.

### 5. Persistência e consulta

Será criada uma tabela própria, por migration Flyway, para os registros persistidos. O
mapeamento físico proposto é:

| Domínio | Coluna |
|---|---|
| eventId | `event_id` |
| schemaVersion | `schema_version` |
| occurredAt | `occurred_at` |
| level | `level` |
| service | `service` |
| component | `component` |
| operation | `operation` |
| message | `message` |
| correlationId | `correlation_id` |
| documentId | `document_id` |
| details | `details_json` |
| persistedAt | `persisted_at` |

`event_id` terá unicidade para idempotência. Índices deverão priorizar `occurred_at`,
`level`, `document_id` e `correlation_id` conforme a consulta aprovada no plano.

A API será somente leitura em `GET /logs` e não exporá SQL, RabbitMQ, storage ou
credenciais. A visualização será uma rota separada `/logs`, local/read-only, com
paginação e filtros por nível, serviço, `documentId`, operação, intervalo de tempo e
correlação.

Os registros persistidos terão retenção de três horas a partir de `occurred_at`. Um
processo agendado removerá registros vencidos de forma idempotente. A limpeza não
alterará documentos, notificações ou logs locais mais recentes.

### 6. Independência do domínio de documentos

O serviço de logs não terá permissão para alterar documento, status, contador de falhas,
retry, staging, storage ou exclusão. O produtor registra o fato; o consumidor persiste a
evidência. Se a publicação falhar, o log local registra a falha e o fluxo original
continua.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Persistir cada log diretamente no PostgreSQL dentro da transação do documento | Aumenta latência e acopla indisponibilidade de logging ao fluxo principal. |
| Usar somente logs locais do processo | Dificulta consulta histórica, correlação e visualização centralizada. |
| Usar somente RabbitMQ | Pode perder evidência quando o broker estiver indisponível e não substitui stdout/stderr operacional. |
| Criar ELK/OpenSearch/Grafana agora | Adiciona infraestrutura e operação incompatíveis com a primeira versão do portfólio. |
| Criar um microserviço separado imediatamente | Aumenta deploy, configuração e observabilidade antes de haver necessidade de escala. |
| Reutilizar a exchange de notificações | Mistura auditoria interna com eventos destinados ao usuário e torna os contratos acoplados. |
| Expor logs diretamente pelo RabbitMQ/PostgreSQL | Viola os limites entre frontend, API e infraestrutura e pode vazar segredos. |

## Consequências

A aplicação terá diagnóstico local mesmo durante falhas do RabbitMQ e uma consulta
centralizada dos eventos relevantes quando o broker e o consumidor estiverem disponíveis.
O uso de `eventId`, publisher confirm, retries, DLQ e persistência idempotente permite
reentrega sem duplicar registros.

O custo é criar uma migration, uma topologia RabbitMQ adicional, um consumidor, uma API
read-only e uma visualização. A solução não oferece captura de cada log de baixo nível,
retenção infinita, autenticação ou garantia de persistência quando todos os caminhos
locais e RabbitMQ estiverem indisponíveis.

## Decisões pendentes de aprovação

- A visualização será a rota separada `/logs`.
- A retenção dos registros persistidos será de três horas.
- A consulta terá paginação e filtros por nível, serviço, `documentId`, operação,
  intervalo de tempo e correlação.
- A captura inicial não excluirá categorias funcionais de log; somente a sanitização de
  segredos será obrigatória.
