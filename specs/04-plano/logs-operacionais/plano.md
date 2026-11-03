# Plano de Implementação — Logs operacionais e auditoria

Spec relacionada: specs/03-features/logs-operacionais/spec.md  
ADR relacionada: specs/02-arquitetura/DECISAO/ADR-009-mensageria-para-logs-operacionais.md  
Status: Aprovado

## 1. Resumo técnico

O DocFlow terá um serviço lógico de logs dentro do backend existente. A aplicação
produzirá eventos estruturados de todos os níveis disponíveis no fluxo de logging, sem
exclusão funcional inicial. O evento será sanitizado, emitido para o logger local e,
quando persistível, publicado no RabbitMQ.

Um consumidor idempotente persistirá os eventos em uma tabela própria do PostgreSQL.
Uma rotina agendada removerá registros com mais de três horas. A API read-only
GET /logs oferecerá paginação e filtros por nível, serviço, componente, operação,
documentId, correlationId e intervalo de tempo. O frontend terá uma rota separada
/logs, sem ações de alteração do domínio.

RabbitMQ usará uma topologia própria, com publisher confirm, retries 5s/15s/60s, DLX e
DLQ. Se RabbitMQ estiver indisponível, o logger local continuará registrando o evento e
o fluxo principal não será bloqueado. O serviço de logs não poderá alterar documentos,
status, retries, staging, storage ou exclusões.

A migration será a próxima versão disponível depois da V6 existente no repositório,
sem editar migrations anteriores. O nome da migration deve ser confirmado no momento da
implementação com o estado real do Flyway.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| backend/.../document/application/ | Criar contexto/fábrica de eventos, sanitização e emissão nos pontos relevantes. | Alto: log antecipado, duplicado ou com segredo. |
| backend/.../document/messaging/rabbitmq/ | Adicionar contrato, publisher, consumidor, topologia, retry e DLQ de logs. | Alto: ack, reentrega e falha do broker. |
| backend/.../document/adapter/out/persistence/ | Persistir eventos e consultar filtros/paginação. | Alto: migration, volume e retenção. |
| backend/.../document/adapter/in/web/ | Expor GET /logs sem permitir mutações. | Médio: contrato de consulta e vazamento operacional. |
| backend/.../document/application/ | Adicionar rotina de retenção de três horas. | Médio: remoção prematura ou impacto no banco. |
| backend/src/main/resources/db/migration/ | Criar migration expand-only para a tabela de logs e índices. | Alto: schema e rollback. |
| frontend/src/app/logs/ | Criar rota /logs, tabela/consulta, filtros e paginação read-only. | Médio: desempenho e exposição de dados. |
| infra/.env.example e configuração | Documentar retenção, parâmetros RabbitMQ e logging sem segredos. | Alto: configuração divergente ou credencial exposta. |
| docs/ e specs/ | Documentar contrato, operação, retenção e DLQ. | Baixo: divergência documental. |

O serviço de logs será executado no backend atual; não haverá novo deployable, broker,
storage externo ou painel SaaS nesta rodada. O frontend não acessará PostgreSQL,
RabbitMQ, MinIO ou qualquer logger diretamente.

## 3. Componentes novos

- Evento `ApplicationLogEvent` versionado e sanitizado.
- Contexto/fábrica para `eventId`, `occurredAt`, `correlationId` e `documentId`.
- Porta de publicação de logs e publisher RabbitMQ com fallback local.
- Topologia própria `docflow.application-logs` com filas persist, retry, DLX e DLQ.
- Consumer idempotente com chave única `event_id`.
- Entidade/repositório de logs e migration V7 ou próxima versão disponível.
- Scheduler de retenção por `occurred_at` com janela de três horas.
- API `GET /logs` com paginação e todos os filtros aprovados.
- Rota Angular `/logs` com listagem, filtros, paginação, estado vazio, erro e loading.
- Testes de contrato, RabbitMQ/Testcontainers, PostgreSQL, API, Angular e E2E.

## 4. Contratos e dados

### 4.1 Evento interno

O payload mínimo é:

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

O campo level deve aceitar os níveis disponíveis no logger da aplicação, mantendo no
mínimo INFO, WARN e ERROR. O contrato não pode transportar segredo, conteúdo binário,
API key, token ou stack trace não sanitizado.

### 4.2 Persistência

A tabela proposta é `application_logs`, com:

- `event_id` único;
- `schema_version`;
- `occurred_at`;
- `level`;
- `service`;
- `component`;
- `operation`;
- `message`;
- `correlation_id` nullable;
- `document_id` nullable;
- `details_json` nullable;
- `persisted_at`.

Índices devem cobrir `occurred_at`, `level`, `service`, `document_id` e
`correlation_id`. A limpeza remove registros cujo `occurred_at` seja anterior a
`now - 3 hours`.

### 4.3 API de consulta

Contrato inicial proposto:

```text
GET /logs
  ?page=0
  &size=50
  &level=ERROR
  &service=document-api
  &component=document-storage-consumer
  &operation=document.processing
  &documentId={uuid}
  &correlationId={uuid}
  &occurredFrom={iso-8601}
  &occurredTo={iso-8601}
```

A resposta será read-only e paginada, com itens e metadados de página. `size` terá um
limite máximo configurado para impedir carregamento ilimitado. A ordenação padrão será
`occurred_at` decrescente, com desempate por `event_id`.

### 4.4 RabbitMQ

| Elemento | Nome |
|---|---|
| Exchange | `docflow.application-logs` |
| Tipo | `topic` |
| Fila de persistência | `docflow.application-logs.persist` |
| Routing key INFO | `application.log.info` |
| Routing key WARN | `application.log.warn` |
| Routing key ERROR | `application.log.error` |
| Retry 5s | `docflow.application-logs.retry.5s` |
| Retry 15s | `docflow.application-logs.retry.15s` |
| Retry 60s | `docflow.application-logs.retry.60s` |
| DLX | `docflow.application-logs.dlx` |
| DLQ | `docflow.application-logs.dlq` |

Exchange, filas e mensagens serão duráveis. O consumidor fará ACK após persistência
confirmada ou duplicidade idempotente. Erro permanente de validação ou persistência vai
para DLQ; erro transitório usa as três filas de retry.

## 5. Sequência de implementação

### Fase 1 — Base

1. Fechar matriz FR/BR/AC e baseline dos logs atuais, incluindo mudanças já presentes
   no fluxo de notificações sem reescrevê-las.
2. Fechar contrato do evento, níveis disponíveis, sanitização, correlationId,
   documentId e comportamento de fallback.
3. Fechar contrato `GET /logs`, filtros, paginação, ordenação e ProblemDetail.
4. Fechar topologia RabbitMQ, retries, publisher confirm, ack/nack, DLX e DLQ.
5. Confirmar migration da tabela e índices sem editar migrations existentes.
6. Preparar fixtures de eventos, configuração de retenção e dados de teste.

Gate da Fase 1: contrato, topologia, migration e consulta foram revisados; o baseline
existente passa antes da lógica nova.

### Fase 2 — Lógica principal

1. Implementar evento, contexto de correlação e sanitizador.
2. Integrar logger local estruturado e publisher assíncrono sem bloquear operações.
3. Instrumentar upload, processamento, retry, falhas, warnings, notificações, exclusão,
   reconciliação e erros de infraestrutura relevantes.
4. Implementar migration V7 ou próxima versão livre para `application_logs`, constraint
   de `event_id` e índices.
5. Implementar repositório idempotente e consumer RabbitMQ.
6. Implementar retries 5s/15s/60s, DLX/DLQ e recuperação operacional.
7. Implementar scheduler de retenção de três horas com clock testável.
8. Implementar serviço e controller `GET /logs` com todos os filtros aprovados.
9. Garantir que o serviço de logs não altere o domínio de documentos e não crie loop
   registrando indefinidamente os próprios erros de persistência.

Gate da Fase 2: eventos são emitidos e persistidos, duplicidade é controlada, retenção
funciona e falhas de logging não afetam documentos.

### Fase 3 — Interface

1. Criar a rota `/logs` e o serviço Angular read-only.
2. Criar listagem paginada com timestamp, nível, serviço, operação, mensagem segura,
   correlação e documento.
3. Criar filtros por nível, serviço, componente, operação, documentId, correlationId e
   intervalo de tempo.
4. Criar estados de loading, lista vazia, erro, nova tentativa e limite de página.
5. Criar navegação segura para o documento associado, sem ação de alteração.
6. Garantir que a interface não exiba stack trace ou detalhes classificados como segredo
   e não acesse infraestrutura diretamente.

Gate da Fase 3: `/logs` consulta, filtra e pagina registros, mantém o Hub funcionando e
não permite mutações.

### Fase 4 — Testes

1. Cobrir evento, níveis, sanitização, correlação e fallback local.
2. Cobrir pontos de emissão de sucesso, warning, falha, retry, exclusão e infraestrutura.
3. Cobrir migration, constraints, índices, repository, idempotência e retenção em
   PostgreSQL/Testcontainers.
4. Cobrir topologia RabbitMQ, publisher confirm, persistência, retries, DLX/DLQ, nack e
   indisponibilidade do broker.
5. Cobrir `GET /logs` com cada filtro, paginação, ordenação, validação e erro.
6. Cobrir rota `/logs`, filtros, loading, vazio, erro, paginação e navegação no Angular.
7. Executar E2E com eventos de documento e consulta de logs sem vazar segredos.
8. Executar build e regressão completa do backend/frontend, incluindo notificações e Hub.

Gate da Fase 4: AC-001 a AC-010 passam; retenção, filtros, DLQ, sanitização e regressão
estão evidenciados.

### Fase 5 — Entrega

1. Atualizar API, banco, operação, RabbitMQ, retenção, configuração e README.
2. Documentar a rota `/logs`, os filtros e o limite de três horas.
3. Documentar o procedimento para inspecionar e recuperar a DLQ sem alterar documentos.
4. Executar git diff --check, build, testes prioritários e inspeção de segredos.
5. Executar smoke test com log de sucesso, warning, erro, retry, exclusão, filtro e
   expiração de retenção usando clock/configuração controlada.
6. Revisar Spec ↔ ADR ↔ Plano/Tarefas ↔ Código ↔ Testes e obter aprovação de Marcos
   antes de qualquer deploy.

Gate da Fase 5: documentação, migração, rollback, testes e evidências revisados; nenhum
deploy é feito sem aprovação final.

## 6. Riscos

| Risco | Chance | Impacto | Mitigação | Merece ADR? |
|---|---|---|---|---|
| Capturar todos os níveis gera grande volume. | Alta | Médio | Retenção de 3h, paginação, índices e monitoramento; nenhuma exclusão funcional inicial. | Já coberto pela ADR-009. |
| Log contém segredo ou conteúdo sensível. | Média | Alto | Sanitizador obrigatório, testes de payload e inspeção de API/banco/frontend. | Já coberto pela ADR-009. |
| RabbitMQ indisponível durante uma falha. | Média | Alto | Log local estruturado como fallback; fluxo de documento não depende do consumer. | Já coberto pela ADR-009. |
| Consumer registra o próprio erro e cria loop. | Média | Alto | Falhas do pipeline de logs usam logger local controlado e não republicam recursivamente. | Não; regra da implementação. |
| Retenção apaga registros antes do esperado. | Baixa | Alto | Clock testável, comparação por occurred_at, scheduler idempotente e integração PostgreSQL. | Não; coberto por teste. |
| API `/logs` expõe dados operacionais sem autenticação. | Alta | Alto | Escopo local explícito, read-only e sem exposição externa; autenticação exige nova decisão. | Já coberto pela ADR-009. |
| Migration conflita com versão adicionada pelo Hub. | Média | Alto | Verificar Flyway no início da implementação e usar próxima versão disponível; nunca editar V1–V6. | Não; procedimento de migration. |
| Consulta de filtros degrada o PostgreSQL. | Média | Médio | Índices aprovados, limite de size, filtros validados e testes de plano de consulta. | Não; otimização operacional. |

## 7. Decisões antes da implementação

- Spec de logs operacionais aprovada por Marcos.
- ADR-009 aceita por Marcos.
- Visualização em `/logs`.
- Retenção persistida de três horas.
- Filtros completos e paginação cronológica.
- Captura inicial sem exclusões funcionais, mantendo sanitização de segredos.
- Este plano e suas tarefas ainda precisam ser aprovados antes da implementação.
