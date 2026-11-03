# Tarefas de Implementação — Logs operacionais e auditoria

Spec relacionada: specs/03-features/logs-operacionais/spec.md  
Plano relacionado: specs/04-plano/logs-operacionais/plano.md  
ADR relacionada: specs/02-arquitetura/DECISAO/ADR-009-mensageria-para-logs-operacionais.md  
Status: Aprovado

As tarefas devem ser executadas em ordem. Cada tarefa só pode ser marcada como pronta
quando sua verificação estiver registrada e o gate da fase anterior continuar verde.

## Fase 1 — Base

- [x] T-001 [FR-001 a FR-013][BR-001 a BR-011][AC-001 a AC-010] Montar a matriz de
  rastreabilidade da feature.
  - Arquivos/módulos: Spec, ADR-009, plano, tarefas e baseline do backend/frontend.
  - Verificação: cada requisito aponta para contrato, componente e teste; nenhum requisito
    de autenticação, histórico infinito ou mutação de documento é criado por inferência.
  - Evidência: `matriz-rastreabilidade.md` criada com rastreabilidade de requisitos,
    componentes, testes e limites de escopo.

- [x] T-002 [FR-001 a FR-006] Registrar o baseline de logging e mensageria atual.
  - Arquivos/módulos: backend, configurações de logging, RabbitMQ, fluxo de documentos e
    alterações já existentes do Hub/notificações.
  - Verificação: estão documentados os pontos atuais de sucesso, warning, erro, retry,
    exclusão, fallback local e ausência da consulta `/logs`.
  - Evidência: `baseline.md` registra o estado atual, lacunas e comportamentos que não
    podem sofrer regressão.

- [x] T-003 [FR-001][FR-003][FR-012] Fechar o contrato `ApplicationLogEvent`.
  - Arquivos/módulos: contratos em document/messaging/rabbitmq/ e documentação.
  - Verificação: eventId, schemaVersion, occurredAt, level, service, component,
    operation, message, correlationId, documentId e details estão definidos; níveis
    disponíveis no logger são suportados e segredos são proibidos.
  - Evidência: `contrato-evento.md` define o payload v1, níveis, campos opcionais,
    sanitização e idempotência por `eventId`.

- [x] T-004 [FR-008][FR-009][FR-010] Fechar o contrato de `GET /logs` e da rota `/logs`.
  - Arquivos/módulos: docs/api.md, DTOs planejados, query params, paginação e ProblemDetail.
  - Verificação: filtros por level, service, component, operation, documentId,
    correlationId, occurredFrom/occurredTo, page, size e ordenação têm nomes, tipos,
    limites e respostas definidos.
  - Evidência: `contrato-api.md` define query params, paginação, ordenação, respostas
    200/400/503 e limites de segurança.

- [x] T-005 [FR-004][FR-005][FR-007][FR-011] Fechar a topologia RabbitMQ.
  - Arquivos/módulos: ADR-009, configuração RabbitMQ, fixtures e bindings.
  - Verificação: exchange, filas persist/retry, routing keys, durabilidade, TTLs,
    publisher confirm, ack/nack, DLX e DLQ coincidem com a ADR.
  - Evidência: `topologia.md` consolida a topologia aprovada na ADR-009.

- [x] T-006 [FR-005][FR-013] Preparar migration e retenção.
  - Arquivos/módulos: backend/src/main/resources/db/migration/ e configuração de
    scheduler/clock.
  - Verificação: a próxima migration livre após V6, tabela `application_logs`, unique
    `event_id`, índices e política `occurred_at < now - 3 hours` estão definidos sem
    editar migrations anteriores.
  - Evidência: `persistencia-retencao.md` define a tabela, constraints, índices,
    retenção e clock testável; a migration será criada na T-012.

- [x] T-007 [FR-006][FR-012] Preparar fixtures de fallback, sanitização e segurança.
  - Arquivos/módulos: testes backend, `.env.example`, configuração e utilitários.
  - Verificação: testes conseguem simular RabbitMQ indisponível, payload com segredo,
    evento inválido e logger local sem chamar serviços externos.
  - Evidência: `fixtures-seguranca.md` registra os casos determinísticos e os limites;
    a execução automatizada será evidenciada nas tarefas T-023 e T-026.

**Gate da Fase 1:** matriz, contratos, topologia, migration e configuração foram
revisados; o baseline existente passa antes da lógica nova.

## Fase 2 — Lógica principal

- [x] T-008 [FR-001][FR-003][FR-012] Implementar `ApplicationLogEvent`, contexto e
  sanitizador.
  - Arquivos/módulos: document/application/, document/messaging/rabbitmq/ e porta de
    logging.
  - Verificação: eventos têm campos obrigatórios, IDs/correlação consistentes, níveis
    disponíveis, detalhes estruturados e nenhum segredo/conteúdo binário.
  - Evidência: `ApplicationLogEventContractTest` cobre schema v1, níveis, contexto,
    timestamp ISO-8601, sanitização de segredos/binários e rejeição de campos obrigatórios.

- [x] T-009 [FR-001][FR-006] Integrar logger local estruturado como caminho obrigatório.
  - Arquivos/módulos: configuração de logging, adapter/port de logging e testes.
  - Verificação: evento é escrito localmente mesmo com RabbitMQ indisponível; a resposta
    do fluxo principal não fica bloqueada pela persistência remota.
  - Evidência: `ApplicationLogRecorderTest` comprova escrita local antes do agendamento,
    retenção local quando o publisher falha e tolerância a executor rejeitado; o writer
    SLF4J emite o evento já sanitizado com campos estruturados.

- [x] T-010 [FR-002][FR-003] Instrumentar eventos de documentos e infraestrutura.
  - Arquivos/módulos: upload, processamento, retry, falha, reconciliação, exclusão,
    notificações, storage e tratamento de erros.
  - Verificação: sucesso, warning, erro, início, conclusão, retry e exclusão produzem
    eventos com contexto; a captura inicial não aplica lista funcional de exclusões.
  - Evidência: `DocumentService`, `DocumentStorageMessageConsumer`,
    `DocumentReconciliationService`, scheduler, exception handler e consumidores de
    notificações emitem eventos sanitizados; `DocumentOperationalLogEmissionTest` cobre
    dispatch bem-sucedido, dispatcher ausente e falha de canal sem propagar a exceção.

- [x] T-011 [FR-004][FR-005][FR-007][FR-011] Implementar publisher e topologia RabbitMQ.
  - Arquivos/módulos: document/messaging/rabbitmq/, publisher confirms, exchanges, filas,
    retry, DLX e DLQ.
  - Verificação: mensagens persistentes usam a routing key correta; falha transitória
    percorre 5s/15s/60s; evento inválido/permanente chega à DLQ; não há requeue infinito.
  - Evidência: `ApplicationLogRabbitTopology`, `ApplicationLogRabbitConfiguration` e
    `RabbitMqApplicationLogPublisher` declaram os nomes aprovados, mensagens persistentes,
    routing keys INFO/WARN/ERROR, publisher confirms, retries 5s/15s/60s e DLX/DLQ;
    `ApplicationLogRabbitTopologyTest` valida o contrato estático.

- [x] T-012 [FR-005][FR-007][FR-011] Criar migration, entidade e repository de logs.
  - Arquivos/módulos: migration seguinte à V6, adapter/out/persistence, entidade e
    constraints.
  - Verificação: `event_id` é único, campos nullable estão corretos, índices existem e
    documentos existentes continuam legíveis.
  - Evidência: migration `V7__create_application_logs.sql` cria `application_logs` de
    forma expand-only, chave primária/única em `event_id`, JSONB, constraints de schema/
    level e índices de consulta; `ApplicationLogEntityTest` valida o mapeamento isolado.

- [x] T-013 [FR-005][FR-007][FR-011] Implementar consumer idempotente de logs.
  - Arquivos/módulos: consumer RabbitMQ, service de persistência e tratamento ack/nack.
  - Verificação: evento válido é persistido antes do ACK; duplicidade não cria segunda
    linha; erro permanente vai à DLQ; consumer não altera Document.
  - Evidência: `ApplicationLogPersistenceService` usa `eventId` e trata corrida de
    unicidade; `RabbitMqApplicationLogMessageHandler` faz ACK após persistência ou
    duplicidade e limita retries; `ApplicationLogMessageConsumerTest` verifica validação
    sem tocar no domínio e persistência antes do ACK.

- [x] T-014 [FR-013][BR-011] Implementar retenção automática de três horas.
  - Arquivos/módulos: scheduler/application service, repository e configuração de clock.
  - Verificação: registros anteriores a três horas são removidos; registros recentes,
    documentos e logs locais não são afetados; execução repetida é idempotente.
  - Evidência: `ApplicationLogRetentionService` usa `occurredAt < now - PT3H`, clock
    injetável, `deleteByOccurredAtBefore` e intervalo configurável; o teste verifica o
    cutoff determinístico e a operação repetível sem tocar em `Document`.

- [x] T-015 [FR-008][FR-010] Implementar serviço e controller `GET /logs`.
  - Arquivos/módulos: application/query service, controller, DTOs, mapper e
    ProblemDetail.
  - Verificação: paginação, ordenação decrescente e todos os filtros retornam somente
    dados seguros; size acima do limite é rejeitado ou limitado conforme contrato.
  - Evidência: `ApplicationLogQueryService` valida page/size/level/UUID/ISO-8601/range,
    aplica filtros combináveis e ordena por `occurredAt DESC, eventId DESC`; controller
    expõe somente GET `/logs`, respostas paginadas e ProblemDetail seguro para 400/503.
    `ApplicationLogQueryServiceTest` cobre defaults, limite/range e falha de banco.

- [x] T-016 [FR-007][FR-011][BR-007] Impedir loop e acoplamento do serviço de logs.
  - Arquivos/módulos: consumer, publisher, tratamento de exceções e testes de isolamento.
  - Verificação: erro de persistência registra fallback local sem republicação recursiva;
    o serviço não altera status, failure_count, retry, staging, storage ou exclusão.
  - Evidência: `ApplicationLogRecorder` trata falha local/remota sem lançar para o fluxo
    de negócio e sem republicar o erro; `ApplicationLogRecorderTest` verifica uma única
    tentativa remota; `ApplicationLogMessageConsumer` depende apenas do repositório de
    logs, sem referência a `Document`.

**Gate da Fase 2:** eventos são emitidos, persistidos e consultáveis; retenção, retry,
DLQ, idempotência e fallback funcionam sem alterar o domínio de documentos.

## Fase 3 — Interface

- [x] T-017 [FR-009][FR-010] Criar a rota Angular `/logs`.
  - Arquivos/módulos: frontend/src/app/logs/, app.routes.ts e shell de navegação.
  - Verificação: `/logs` abre uma tela read-only separada do Hub e não acessa diretamente
    PostgreSQL, RabbitMQ, MinIO ou o logger do backend.
  - Evidência: rota lazy `/logs`, shell com navegação Hub/Logs e tela read-only inicial em
    `frontend/src/app/logs/`; `logs-page.spec.ts` valida renderização sem ações e navegação
    para `/logs` (3 testes direcionados passando).

- [x] T-018 [FR-008][FR-009] Implementar serviço Angular de consulta.
  - Arquivos/módulos: logs-api.service.ts, modelos, query params e mapeador de erros.
  - Verificação: GET /logs envia filtros/paginação corretos, interpreta resposta e trata
    400/500/indisponibilidade sem expor detalhes internos.
  - Evidência: `LogsApiService` usa somente GET para a URL configurada do backend, envia
    paginação e filtros aprovados, e `mapApplicationLogError` converte 400/503/erro de
    rede em mensagens seguras; `logs-api.service.spec.ts` passou 3 testes direcionados.

- [x] T-019 [FR-008][FR-009] Implementar listagem paginada e apresentação dos campos.
  - Arquivos/módulos: página/template/estilos de logs.
  - Verificação: cada registro exibe timestamp, nível, serviço, componente, operação,
    mensagem segura, correlação e documento quando disponíveis; ordenação é clara.
  - Evidência: `LogsPage` consome a resposta paginada, renderiza tabela read-only com os
    campos do contrato e identifica os níveis; `logs-page.spec.ts` cobre registro paginado
    e passou 4 testes da página antes da inclusão dos filtros.

- [x] T-020 [FR-008][FR-009][FR-010] Implementar filtros completos.
  - Arquivos/módulos: formulário/filtros da rota `/logs` e serviço Angular.
  - Verificação: level, service, component, operation, documentId, correlationId,
    occurredFrom e occurredTo podem ser combinados e resetados.
  - Evidência: formulário read-only com os oito filtros aprovados, conversão de datas para
    ISO-8601 e reset para defaults; `logs-page.spec.ts` passou 5 testes incluindo a
    combinação completa e limpeza dos filtros.

- [x] T-021 [FR-009][FR-010] Implementar estados de loading, vazio, erro e paginação.
  - Arquivos/módulos: página `/logs`, componentes e testes de UI.
  - Verificação: a tela não carrega histórico ilimitado, permite nova tentativa, preserva
    filtros válidos e não oferece edição/exclusão/reprocessamento.
  - Evidência: `LogsPage` usa página inicial limitada a 50, controles server-side anterior/
    próxima, estados loading/vazio/erro e retry manual; `logs-page.spec.ts` passou 7 testes,
    incluindo limite, preservação de filtro, retry e ausência de stack trace na UI.

- [x] T-022 [FR-010][FR-012] Implementar navegação segura para documento relacionado.
  - Arquivos/módulos: logs page, rotas existentes e sanitização de apresentação.
  - Verificação: documentId válido abre acompanhamento; ausência ou documento removido
    não recria nem altera documento; segredos e stack traces não aparecem.
  - Evidência: `LogsPage` só cria `RouterLink` para documentId UUID válido; valores ausentes
    ou inválidos permanecem texto e não disparam mutações. `logs-page.spec.ts` passou 8
    testes, incluindo link para acompanhamento e bloqueio de identificador inválido.

**Gate da Fase 3:** `/logs` lista, filtra e pagina os registros, possui estados de erro e
continua isolada das mutações do Hub/documento.

## Fase 4 — Testes

- [x] T-023 [FR-001][FR-003][FR-012][AC-001][AC-002][AC-008] Cobrir evento, níveis,
  contexto e sanitização.
  - Arquivos/módulos: testes unitários de factory, sanitizador, logger e serializers.
  - Verificação: sucesso, warning, error, baixo nível, correlação, documentId e campos
    proibidos têm comportamento determinístico.
  - Evidência: `ApplicationLogEventContractTest` valida schema/timestamp ISO, níveis INFO/
    WARN/ERROR, contexto, detalhes e sanitização de segredo/binário; `ApplicationLogRecorderTest`
    valida fallback local, falha de agendamento e ausência de republicação. Execução direcionada
    passou com código 0.

- [x] T-024 [FR-002][FR-003][AC-001][AC-002] Cobrir pontos de emissão do ciclo do documento.
  - Arquivos/módulos: testes de upload, processamento, retry, falha, reconciliação,
    notificação e exclusão.
  - Verificação: cada evento esperado é produzido no ponto correto e uma falha do log
    não muda o resultado do documento.
  - Evidência: testes de `DocumentService`, `DocumentStorageMessageConsumer`,
    `DocumentReconciliationService` e `DocumentOperationalLogEmissionTest` cobrem aceitação,
    processamento concluído/retry/falha, reconciliação, notificação e exclusão; a rodada
    direcionada final passou com código 0 e os cenários de falha mantiveram o fluxo do documento.

- [x] T-025 [FR-005][FR-007][FR-013][AC-003][AC-005][AC-009] Cobrir PostgreSQL, migration,
  idempotência e retenção.
  - Arquivos/módulos: Testcontainers PostgreSQL, repository, scheduler e migration.
  - Verificação: unique event_id, filtros, índices, limpeza de registros vencidos,
    preservação de registros recentes e execução repetida são verificados.
  - Evidência: `ApplicationLogPostgresIntegrationTest` executa Testcontainers PostgreSQL,
    aplica as 7 migrations até V7, verifica tabela/5 índices, idempotência por eventId,
    filtros por nível/serviço/componente/operação/documentId e retenção de 3 horas; rodada
    passou com código 0.

- [x] T-026 [FR-004][FR-005][FR-006][FR-011][AC-003][AC-004][AC-007] Cobrir RabbitMQ real.
  - Arquivos/módulos: Testcontainers RabbitMQ, topologia, publisher, consumer, retries,
    DLX e DLQ.
  - Verificação: publisher confirm, persistência, fallback, ack/nack, 5s/15s/60s,
    mensagem inválida e falha permanente são observáveis.
  - Evidência: `ApplicationLogRabbitRealIntegrationTest` executa RabbitMQ real e valida
    publicação confirmada/persistente, ACK após processamento, retry persistente na fila de
    5s e encaminhamento de mensagem inválida à DLQ; `ApplicationLogRabbitTopologyTest`
    valida as três janelas 5s/15s/60s. Rodada Testcontainers passou com código 0.

- [x] T-027 [FR-008][FR-010][AC-006] Cobrir controller e query API.
  - Evidência: `ApplicationLogControllerTest` valida resposta paginada com todos os filtros,
    consulta padrão vazia e mapeamento seguro de validação (400) e indisponibilidade do
    banco (503), sem expor detalhes internos; rodada Maven passou com código 0.
  - Arquivos/módulos: testes MVC/controller, DTOs, filtros e ProblemDetail.
  - Verificação: cada filtro, combinação, paginação, ordenação, limite de size, intervalo
    inválido e erro de banco possui resposta conforme contrato.

- [x] T-028 [FR-009][FR-010][AC-006][AC-010] Cobrir rota e serviço Angular.
  - Evidência: `logs-api.service.spec.ts` valida GET com todos os filtros/paginação,
    omissão de filtros vazios e mensagens seguras; `logs-page.spec.ts` valida listagem,
    filtros, paginação limitada, loading/vazio/erro/retry, link de documento UUID e rota
    `/logs`; suíte Angular completa passou com 110 testes e código 0.
  - Arquivos/módulos: logs-api.service.spec.ts, página `/logs` e componentes.
  - Verificação: listagem, filtros completos, paginação, loading, vazio, erro, documento
    associado e ausência de categorias excluídas estão cobertos.

- [x] T-029 [FR-001 a FR-013][AC-001 a AC-010] Executar E2E e regressão completa.
  - Evidência: `docker compose --env-file .env config --quiet` passou; backend completo
    passou com 255 testes em 61 relatórios, sem falhas/erros, incluindo PostgreSQL,
    RabbitMQ, MinIO, fluxo de documentos, notificações, logs, retenção e DLQ; frontend
    completo passou com 110 testes em 12 arquivos, incluindo a rota `/logs`, filtros e
    paginação. Warnings observados pertencem a cenários de falha/retry deliberadamente
    exercitados e não produziram falha de teste.
  - Arquivos/módulos: backend, frontend, PostgreSQL, RabbitMQ, MinIO, Compose e fixtures.
  - Verificação: fluxo de documento, notificações, logs, rota `/logs`, filtros, retenção
    controlada, DLQ e operações existentes passam sem regressão óbvia.

**Gate da Fase 4:** AC-001 a AC-010 passam com evidência; nenhum segredo aparece e as
features Hub/notificações continuam verdes.

## Fase 5 — Entrega

- [x] T-030 [FR-008][FR-009][FR-011][FR-013] Atualizar documentação de API, banco e
  operação.
  - Arquivos/módulos: docs/api.md, docs/database.md, docs/architecture.md, README.md,
    documentação RabbitMQ e specs/06-deploy/.
  - Verificação: configuração, `/logs`, filtros, retenção, migration, retry/DLQ e
    recuperação operacional são descritos sem conhecimento implícito.
  - Evidência: `docs/api.md` documenta `GET /logs`, filtros, paginação e ProblemDetail;
    `docs/database.md` documenta a V7 e retenção; `docs/architecture.md`, README e
    `specs/06-deploy/logs-operacionais/deploy.md` documentam topologia, configuração,
    smoke test, diagnóstico e rollback.

- [x] T-031 [FR-006][FR-012] Executar inspeção de segurança e privacidade.
  - Evidência: busca de segredos literais em arquivos versionados não encontrou chaves,
    tokens, JWTs ou chaves privadas; `infra/.env` não é rastreado. O contrato de logs
    e os testes verificam redaction de segredos, binários, exceções e stack traces; o
    bundle Angular de produção não contém credenciais nem endpoints de infraestrutura.
    O build terminou com código 0; houve apenas warning de budget preexistente do SCSS
    do Hub, sem exposição de dados.
  - Arquivos/módulos: backend, frontend, banco, payloads, logs locais e arquivos de
    ambiente.
  - Verificação: nenhuma senha, API key, token, conteúdo binário, endereço sensível ou
    stack trace não sanitizado aparece no código, banco, API ou bundle.

- [x] T-032 [FR-001 a FR-013] Executar build, smoke test e evidências.
  - Evidência: `mvnw.cmd -q -DskipTests package` e build Angular de produção passaram
    com código 0; o JAR isolado respondeu `200` em `/actuator/health` e `GET /logs`
    (`items=0`, paginação válida), com PostgreSQL/RabbitMQ/MinIO locais ativos. A
    inicialização do Angular em modo dev concluiu e a suíte cobre sucesso, warning,
    erro, retry, filtros, paginação e estados de documento. O runner encerra processos
    HTTP entre comandos; por isso o smoke de navegador persistente não foi tratado como
    evidência adicional. Não houve envio de log externo.
  - Arquivos/módulos: backend, frontend, Compose, PostgreSQL, RabbitMQ, MinIO e checklist.
  - Verificação: sucesso, warning, erro, retry, exclusão, filtros, paginação e expiração
    de três horas com clock controlado são observados; nenhum log real externo é exigido.

- [ ] T-033 [FR-001 a FR-013][AC-001 a AC-010] Revisar convergência e obter aprovação.
  - Arquivos/módulos: Spec, ADR, plano, tarefas, código, migration, testes, docs e
    rollback.
  - Verificação: cada FR/BR/AC aponta para implementação/teste, divergências estão
    registradas, Marcos aprovou e nenhum deploy foi feito nesta tarefa.

## Adiado

- [ ] Autenticação, autorização, multiusuário e exposição pública de `/logs`.
- [ ] Retenção superior a três horas, arquivamento ou histórico legal imutável.
- [ ] Edição, exclusão manual, reprocessamento ou ações de operação pela tela.
- [ ] ELK, OpenSearch, Grafana, SaaS ou microserviço de logs separado.
- [ ] Tracing distribuído completo, métricas avançadas e alertas externos.
- [ ] Mudança de provider, filtros adicionais ou novo contrato sem atualização da Spec/ADR.
