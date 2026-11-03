# Tarefas de Implementação — Notificações de documentos

Spec relacionada: specs/03-features/notificacoes-documentos/spec.md  
Plano relacionado: specs/04-plano/notificacoes-documentos/plano.md  
ADR relacionada: specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md  
Status: Aprovado

As tarefas devem ser executadas em ordem. Cada tarefa só pode ser marcada como pronta
quando sua verificação estiver registrada e o gate da fase anterior continuar verde.

## Fase 1 — Base

- [x] T-001 [FR-001 a FR-014][BR-001 a BR-018][AC-001 a AC-010] Montar a matriz de
  rastreabilidade da feature.
  - Arquivos/módulos: Spec, ADR-008, este plano, tarefas e baseline do backend/frontend.
  - Verificação: cada FR/BR/AC aponta para contrato, módulo e teste planejado; nenhum
    requisito de autenticação, histórico ou migration aparece por inferência.

  - Evidência: `specs/04-plano/notificacoes-documentos/matriz-rastreabilidade.md`.
    O baseline e os limites de escopo foram registrados; não houve alteração em código
    de produção. A rodada corretiva validou o baseline com 85 testes frontend e 27 testes
    backend prioritários aprovados.

- [x] T-002 [FR-003 a FR-008] Registrar o baseline dos pontos de transição do documento.
  - Arquivos/módulos: Document, DocumentService/application, consumidores de storage,
    exclusão manual e testes existentes.
  - Verificação: ficam identificados os pontos em que PROCESSING, COMPLETED, FAILED,
    agenda de exclusão e remoção definitiva podem emitir eventos sem antecipação.
  - Evidência: `specs/04-plano/notificacoes-documentos/baseline-transicoes.md`; a lacuna
    da reconciliação sem `DocumentDeletionPublisher` foi registrada sem alterar o fluxo.
    Suíte direcionada: 53 testes aprovados, zero falhas/erros, `BUILD SUCCESS`.

- [x] T-003 [FR-002][FR-003 a FR-008] Fechar o contrato JSON versionado dos eventos.
  - Arquivos/módulos: contratos em document/messaging/rabbitmq/ e documentação de API.
  - Verificação: eventId, schemaVersion, type, documentId e occurredAt estão definidos;
    status, failureCount e exclusionScheduledAt têm regra de presença; nenhum nome
    físico de banco ou broker aparece no contrato público.
  - Evidência: `DocumentLifecycleEvent.java`, `DocumentLifecycleEventType.java`,
    `DocumentLifecycleEventContractTest.java` e seção de contrato lógico em
    `docs/api.md`. A suíte passou com 7 testes, zero falhas/erros e `BUILD SUCCESS`.

- [x] T-004 [FR-003 a FR-008][FR-013][FR-014] Fechar a topologia RabbitMQ da ADR-008.
  - Arquivos/módulos: contrato de infraestrutura, configurações RabbitMQ e fixtures.
  - Verificação: exchange, filas, bindings, TTL 2h30, routing key de e-mail, retries
    30s/5m/30m, DLX/DLQ, durabilidade e publisher confirm coincidem com a ADR.
  - Evidência: `DocumentNotificationRabbitTopology.java`,
    `DocumentNotificationRabbitConfiguration.java` e
    `DocumentNotificationRabbitConfigurationTest.java`. A suíte nova e a configuração
    de storage existente passaram com 7 testes, zero falhas/erros e `BUILD SUCCESS`.

- [x] T-005 [FR-014][BR-015][BR-016] Preparar configuração segura de e-mail.
  - Arquivos/módulos: infra/.env.example, configuração backend e documentação local.
  - Verificação: BREVO_API_KEY e DOCFLOW_NOTIFICATION_DEFAULT_EMAIL aparecem somente como
    placeholders sem valores; o frontend não recebe nenhuma dessas configurações.
  - Evidência: `infra/.env.example`, `backend/src/main/resources/application.properties`
    e `docs/document-registration-operations.md`. Verificação estática aprovada; as
    duas variáveis permanecem vazias e não aparecem no frontend.

- [x] T-006 [FR-001 a FR-014] Criar fixtures e doubles de teste para RabbitMQ, WebSocket
  e Brevo.
  - Arquivos/módulos: backend/src/test, frontend/src/app e utilitários de integração.
  - Verificação: os testes conseguem publicar eventos, abrir conexão WebSocket e simular
    respostas Brevo sem chamar serviços externos reais.
  - Evidência: `DocumentNotificationTestFixtures`,
    `InMemoryDocumentLifecycleEventPublisher`, `FakeBrevoServer` e os doubles de
    WebSocket/fixtures em `frontend/src/app/notifications/testing/`. A suíte backend
    direcionada passou com 2 testes; a suíte frontend direcionada passou com 2 testes;
    ambas sem serviços externos reais.

**Gate da Fase 1:** matriz, baseline, contrato JSON, topologia e configuração foram
revisados; nenhum código de produção foi alterado sem contrato correspondente.

## Fase 2 — Lógica principal

- [x] T-007 [FR-003 a FR-008][BR-003 a BR-009] Implementar os tipos e mapeadores de
  eventos de ciclo de vida.
  - Arquivos/módulos: document/messaging/rabbitmq/, document/application/ e DTOs de
    notificação.
  - Verificação: os cinco tipos públicos são serializados com schemaVersion 1 e não
    carregam bucket, objectKey, routing key ou credenciais.
  - Evidência: `DocumentLifecycleEventMapper.java` e
    `DocumentLifecycleEventMapperTest.java`; os testes de contrato e mapper passaram
    com 6 testes, zero falhas/erros e `BUILD SUCCESS`.

- [x] T-008 [FR-003][BR-003] Publicar DOCUMENT_PROCESSING_STARTED após a transição para
  PROCESSING.
  - Arquivos/módulos: caso de uso de processamento, retry existente e publisher de
    eventos.
  - Verificação: publicação ocorre somente após a transição aceita; uma publicação
    duplicada não cria uma segunda operação de processamento.
  - Evidência: `DocumentLifecycleEventPublisher`,
    `RabbitMqDocumentLifecycleEventPublisher`, integração no consumidor de storage e
    retry do `DocumentService`, com 43 testes direcionados aprovados e `BUILD SUCCESS`.

- [x] T-009 [FR-004][BR-004] Publicar DOCUMENT_COMPLETED após confirmação do storage.
  - Arquivos/módulos: consumidor de armazenamento, Document e publisher de eventos.
  - Verificação: resultado inconclusivo não publica conclusão; o evento só aparece com
    documento persistido como COMPLETED e conteúdo final confirmado.
  - Evidência: publicação após `save` + `flush` no consumidor de storage e na
    reconciliação; `DocumentCompletedPublicationTest`, `DocumentStorageMessageConsumerTest`
    e `DocumentReconciliationServiceTest` passaram com 18 testes e `BUILD SUCCESS`.

- [x] T-010 [FR-005][BR-005] Publicar DOCUMENT_FAILED após falha efetiva persistida.
  - Arquivos/módulos: tratamento de falha do consumidor de storage e publisher.
  - Verificação: falha de WebSocket/e-mail não incrementa failure_count; cada falha real
    publica o contador correspondente sem alterar regras de retry.
  - Evidência: publicação após persistência no consumidor de storage e na exaustão da
    reconciliação; `DocumentFailedPublicationTest`, `DocumentStorageMessageConsumerTest`
    e `DocumentReconciliationServiceTest` passaram com 18 testes e `BUILD SUCCESS`.

- [x] T-011 [FR-006][FR-007][BR-007] Agendar e publicar DOCUMENT_DELETION_WARNING.
  - Arquivos/módulos: publisher de exclusão/agenda, fila delay.2h30 e topologia RabbitMQ.
  - Verificação: ao chegar a três falhas, a mensagem é agendada; após TTL reduzido no
    teste, o aviso ocorre 30 minutos antes do prazo de três horas; não há exclusão
    antecipada.
  - Evidencia: `DocumentFailedPublicationTest`, `RabbitMqDocumentLifecycleEventPublisherTest`
    e `DocumentNotificationRabbitConfigurationTest` passaram com 10 testes; a terceira
    falha persiste o agendamento, publica o aviso 30 minutos antes do prazo e a topologia
    declara a fila delay.2h30 com TTL de 2h30 e rota aprovada.

- [x] T-012 [FR-008][BR-008] Publicar DOCUMENT_DELETED após exclusão completa.
  - Arquivos/módulos: caso de uso/consumidor de exclusão automática e exclusão manual.
  - Verificação: o evento só é publicado após conteúdo binário, staging e metadados
    tratados; exclusão falha não é reportada como concluída.
  - Evidencia: `DocumentDeletedPublicationTest`, `DocumentServiceTest` e
    `RabbitMqDocumentDeletionMessageHandlerTest` passaram com 34 testes; os caminhos
    manual e automático publicam somente após storage, staging, metadados e flush,
    enquanto falha de exclusão não publica conclusão.

- [x] T-013 [FR-003 a FR-008][FR-013] Implementar a topologia de eventos e o consumidor
  de notificações.
  - Arquivos/módulos: document/messaging/rabbitmq/, configurações de exchange/filas,
    publisher confirms, ack/nack e DLQs.
  - Verificação: mensagens persistentes chegam à fila correta, ack ocorre somente após
    despacho, falhas seguem DLQ/recovery e reentrega não cria efeito indevido.
  - Evidencia: `DocumentNotificationMessageConsumerTest`,
    `RabbitMqDocumentNotificationMessageHandlerTest`,
    `DocumentNotificationRabbitConfigurationTest` e
    `RabbitMqDocumentLifecycleEventPublisherTest` passaram com 14 testes; o consumidor
    valida, deduplica por `eventId`, confirma somente após despacho e rejeita mensagens
    inválidas ou não entregáveis para a DLQ.

- [x] T-014 [FR-009][FR-010][FR-011][FR-012][FR-013] Implementar o handler WebSocket.
  - Arquivos/módulos: adapter/in/websocket ou equivalente dentro do domínio document,
    configuração WebSocket e serviço de broadcast.
  - Verificação: GET /ws/notifications aceita upgrade, todas as conexões ativas recebem
    o evento, ausência de clientes não quebra o fluxo e falha de entrega não altera o
    documento.
  - Evidencia: `DocumentNotificationWebSocketHandlerTest` e
    `DocumentNotificationWebSocketConfigurationTest` passaram com 9 testes; o endpoint
    `/ws/notifications` é registrado, o broadcast percorre todas as sessões abertas e
    falha/fechamento de uma sessão é isolado.

- [x] T-015 [FR-014][BR-014 a BR-018] Criar porta de e-mail e adaptador Brevo.
  - Arquivos/módulos: document/port/out/email/, document/adapter/out/email/brevo/,
    configuração de ambiente e cliente HTTP.
  - Verificação: o adaptador usa a API key somente no backend, envia para o destinatário
    configurado e não expõe segredo ou endereço ao frontend.
  - Evidencia: `BrevoEmailSenderTest` e `DocumentNotificationTestDoublesTest` passaram
    com 6 testes usando servidor HTTP local; o adaptador envia o destinatário e a
    credencial somente no backend e classifica 2xx, 429, 5xx, 4xx e configuração ausente.

- [x] T-016 [FR-014][BR-017][BR-018] Implementar retry e DLQ do e-mail.
  - Arquivos/módulos: filas .retry.30s/.retry.5m/.retry.30m, consumidor de e-mail,
    classificação de respostas Brevo e DLQ.
  - Verificação: timeout/5xx/429 percorrem os três atrasos; 4xx permanente vai direto à
    DLQ; após tentativa inicial e três retries não há nova tentativa automática; o
    documento permanece inalterado.
  - Evidencia: `DocumentEmailMessageConsumerTest`, `RabbitMqDocumentEmailMessageHandlerTest`
    e `DocumentNotificationRabbitConfigurationTest` passaram com 14 testes; 429/timeout/5xx
    usam 30s/5m/30m, 4xx permanente vai direto à DLQ e a confirmação do retry precede o
    ack da mensagem original.

- [x] T-017 [FR-013][FR-014] Isolar falhas de canais do ciclo do documento.
  - Arquivos/módulos: handlers de WebSocket/e-mail, consumidores RabbitMQ e testes de
    erro.
  - Verificação: indisponibilidade de RabbitMQ/WebSocket/Brevo não reverte status, não
    altera failure_count, não cria retry de documento e não desfaz exclusão.
  - Evidencia: `DocumentNotificationChannelDispatcherTest`,
    `RabbitMqDocumentEmailNotificationPublisherTest`, `DocumentNotificationIsolationTest`,
    `DocumentNotificationMessageConsumerTest` e `BrevoEmailSenderTest` passaram com 13
    testes; falha de um canal é isolada e não reverte transição, contador, retry ou
    exclusão do documento.

**Gate da Fase 2:** cinco eventos, WebSocket, e-mail, retries, DLQs e isolamento de
falhas funcionam em testes de contrato e integração; o fluxo de documentos permanece
sem mudança de comportamento não especificada.

## Fase 3 — Interface

- [x] T-018 [FR-001][FR-002][FR-011] Criar o serviço Angular de conexão WebSocket.
  - Arquivos/módulos: frontend/src/app/notifications/ ou módulo equivalente no domínio
    Hub, configuração de endpoint e testes.
  - Verificação: o Hub abre a conexão ao iniciar, reconecta com atraso progressivo e
    não acessa RabbitMQ, Brevo, MinIO ou PostgreSQL.
  - Evidencia: `document-notification.service.spec.ts` e a regressão Angular passaram
    com 91 testes; o serviço usa WebSocket nativo, valida o contrato público, expõe o
    estado da conexão e aplica reconexão progressiva sem acessar infraestrutura interna.

- [x] T-019 [FR-010][FR-011][BR-002] Sincronizar GET /documents ao conectar e reconectar.
  - Arquivos/módulos: Hub, DocumentApiService, serviço de notificações e estado da lista.
  - Verificação: conexão recuperada dispara nova consulta REST; evento perdido não é
    tratado como histórico replayado e a lista reflete o estado atual.
  - Evidencia: `HubPage` e `hub-page.spec.ts` foram integrados ao serviço WebSocket; a
    regressão Angular passou com 92 testes, incluindo nova consulta REST na recuperação
    da conexão e manutenção da carga inicial quando o canal está indisponível.

- [x] T-020 [FR-012][BR-009][BR-013] Implementar estado de sessão e deduplicação.
  - Arquivos/módulos: serviço/estado de notificações do frontend e testes unitários.
  - Verificação: cada aba ativa recebe o evento, o mesmo eventId não duplica a
    apresentação na aba e eventos desconhecidos não quebram o Hub.
  - Evidencia: `notification-session-state.service.ts` e sua spec foram adicionados; a
    regressão Angular passou com 95 testes, cobrindo deduplicação por `eventId`, sessões
    independentes por aba e descarte seguro de payload desconhecido.

- [x] T-021 [FR-009][FR-012] Implementar toast temporário e banner persistente na sessão.
  - Arquivos/módulos: componentes/templates/estilos do Hub e estado de notificações.
  - Verificação: cada evento válido aparece nos dois canais visuais; o banner permanece
    disponível durante a sessão e não substitui a lista de documentos.
  - Evidencia: `HubPage` e `hub-page.spec.ts` integram o estado de sessão ao toast e ao
    banner; a regressão Angular passou com 96 testes, cobrindo os dois canais visuais e
    a semântica `aria-live`.

- [x] T-022 [FR-009][FR-010] Associar ações da notificação ao documento sem recriá-lo.
  - Arquivos/módulos: componentes do Hub, rotas e DocumentApiService.
  - Verificação: documento existente pode abrir acompanhamento/atualizar lista; documento
    removido não é recriado nem recebe ação inválida.
  - Evidencia: `HubPage` atualiza somente documentos presentes, remove o item em
    `DOCUMENT_DELETED` e renderiza acompanhamento apenas para IDs existentes; a
    regressão Angular passou com 98 testes, incluindo documentos ausentes sem link.

- [x] T-023 [FR-001][FR-009][FR-011][FR-013] Revisar acessibilidade, múltiplas abas e
  estados de conexão.
  - Arquivos/módulos: componentes do Hub, estilos, ARIA, teclado e testes de interface.
  - Verificação: toast/banner têm semântica acessível, foco e teclado; cada aba informa
    desconexão/reconexão sem bloquear upload, retry, download ou exclusão.
  - Evidencia: `hub-page.spec.ts`, `notification-session-state.service.spec.ts` e a
    regressão Angular passaram com 99 testes; estados de conexão são anunciados, toast/
    banner têm foco e semântica acessível, e upload permanece habilitado.

**Gate da Fase 3:** o Hub conecta, reconecta, consulta REST, exibe toast/banner em todas
as abas e mantém os fluxos existentes operacionais.

## Fase 4 — Testes

- [x] T-024 [FR-003 a FR-008][BR-003 a BR-009] Cobrir contratos e publicação de eventos.
  - Arquivos/módulos: testes unitários de domínio/aplicação e serializers.
  - Verificação: pontos de emissão, payloads, ordem mínima e ausência de referências de
    infraestrutura estão cobertos para os cinco eventos.
  - Evidencia: `DocumentLifecycleEventContractTest`, `DocumentLifecycleEventMapperTest`,
    `DocumentProcessingStartedPublicationTest`, `DocumentCompletedPublicationTest`,
    `DocumentFailedPublicationTest`, `DocumentDeletedPublicationTest` e
    `RabbitMqDocumentLifecycleEventPublisherTest` passaram com 18 testes.

- [x] T-025 [FR-003 a FR-008][FR-013] Cobrir RabbitMQ com Testcontainers.
  - Arquivos/módulos: testes de topologia, publishers, consumidores e configurações.
  - Verificação: exchange/filas/bindings, mensagens persistentes, publisher confirm,
    ack/nack, TTL, DLX/DLQ e reentrega são observáveis; produção permanece com 2h30.
  - Evidencia: `DocumentNotificationRabbitRealIntegrationTest` passou com 4 testes em
    RabbitMQ real via Testcontainers, cobrindo os cinco eventos, persistência/confirmação,
    ACK/DLQ, reentrega e TTL/DLX; a fila produtiva mantém TTL de 2h30.

- [x] T-026 [FR-014][BR-016 a BR-018] Cobrir o adaptador Brevo sem rede externa.
  - Arquivos/módulos: testes HTTP do adaptador, classificação de erros e consumidor de
    e-mail.
  - Verificação: 2xx, timeout, 429, 5xx, 4xx permanente, credencial ausente, três
    retries e DLQ têm resultados determinísticos.
  - Evidencia: `BrevoEmailSenderTest` e `RabbitMqDocumentEmailMessageHandlerTest`
    passaram com 11 testes, incluindo timeout transitório e os três retries confirmados
    antes da DLQ, sem rede externa.

- [x] T-027 [FR-001][FR-009][FR-011][FR-012][FR-013] Cobrir WebSocket e múltiplas abas.
  - Arquivos/módulos: testes de handler, broadcast, reconexão e estado de sessão.
  - Verificação: todas as conexões recebem eventos, falha de cliente não derruba o
    produtor, deduplicação funciona por eventId e reconnect chama GET /documents.
  - Evidencia: testes backend de WebSocket/consumidor passaram com 9 casos e a suíte
    Angular completa passou com 99 testes, incluindo broadcast, falha isolada,
    deduplicação por `eventId`, duas abas e GET após reconexão.

- [x] T-028 [FR-009][FR-010][FR-011][FR-012] Cobrir o serviço e componentes Angular.
  - Arquivos/módulos: specs do serviço WebSocket, Hub, toast/banner e DocumentApiService.
  - Verificação: eventos válidos/desconhecidos/duplicados, desconexão, reconexão, estado
    vazio, documento removido e apresentação visual estão cobertos.
  - Evidencia: suíte `docflow-frontend` passou com 99 testes em 10 arquivos, cobrindo
    serviço WebSocket, sessão, Hub, toast/banner, `DocumentApiService`, estados vazios,
    documento removido e indicadores visuais.

- [x] T-029 [FR-001 a FR-014][AC-001 a AC-010] Executar E2E e regressão local.
  - Arquivos/módulos: Compose, backend, frontend, PostgreSQL, RabbitMQ, MinIO e mock
    Brevo.
  - Verificação: upload, início, conclusão, falha, terceira falha, aviso, exclusão,
    WebSocket, toast/banner, e-mail simulado e operações existentes passam sem
    regressão óbvia.
  - Evidência: `DocumentRegistrationEndToEndTest` passou com 2 cenários usando
    PostgreSQL, RabbitMQ e MinIO via Testcontainers, incluindo upload, processamento,
    listagem, download, preview e exclusão; a regressão backend passou com 217 testes
    (0 falhas, 0 erros) e a regressão Angular passou com 99 testes em 10 arquivos.
    Os testes de RabbitMQ real, WebSocket, retries/DLQ e Brevo sem rede externa foram
    executados nas T-025 a T-028. O E2E foi ajustado para aguardar `asyncDispatch` nos
    endpoints de streaming, eliminando a corrida do `MockMvc` sem alterar o contrato.

**Gate da Fase 4:** AC-001 a AC-010 passam com evidência automatizada ou de integração;
falhas de canal não afetam documentos e a regressão do Hub permanece verde.

## Fase 5 — Entrega

- [x] T-030 [FR-001 a FR-014] Atualizar documentação operacional e de contratos.
  - Arquivos/módulos: docs/api.md, docs/document-registration-operations.md, README.md,
    docs de RabbitMQ/WebSocket/Brevo, ADR-008 e specs/06-deploy/.
  - Verificação: outra pessoa consegue configurar as variáveis, iniciar o fluxo, testar
    as notificações, interpretar DLQ e entender a ausência de replay/histórico.
  - Evidência: adicionada `docs/document-notifications.md` com contrato público, endpoint
    WebSocket, topologia RabbitMQ, retries/DLQs de e-mail, variáveis Brevo, limites de
    segurança e comandos de validação local; `docs/api.md`,
    `docs/document-registration-operations.md`, `docs/architecture.md`, `README.md`,
    ADR-008 e `specs/06-deploy/hub/deploy.md` foram alinhados ao comportamento implementado.

- [x] T-031 [FR-014][BR-015][BR-016] Revisar configuração e segurança.
  - Arquivos/módulos: .env.example, configuração backend, frontend e análise de Git.
  - Verificação: nenhuma API key, e-mail real, URL de infraestrutura ou credencial aparece
    no código versionado ou no bundle Angular.
  - Evidência: `infra/.env` não é versionado e está protegido pelo `.gitignore`; o
    `infra/.env.example` contém somente credenciais locais de exemplo e as variáveis
    Brevo permanecem vazias. As configurações Angular usam apenas caminhos relativos;
    os defaults `localhost` e o endpoint público da Brevo permanecem somente no
    backend/configuração local. A varredura do bundle existente não encontrou API keys,
    credenciais, endpoints de PostgreSQL/RabbitMQ/MinIO ou `objectKey`.

- [x] T-032 [FR-001 a FR-014] Executar build, testes prioritários e smoke test controlado.
  - Arquivos/módulos: backend, frontend, infra local e mock Brevo.
  - Verificação: build/testes passam; smoke observa cinco eventos, reconexão, múltiplas
    abas, retries/DLQ e confirma que nenhum e-mail real é enviado sem configuração.
  - Evidência: `docker compose ... config --quiet` passou e PostgreSQL, RabbitMQ e MinIO
    estavam ativos/saudáveis. No backend, `.\mvnw.cmd clean package` passou com 217 testes
    e gerou o JAR; `frontend/npm.cmd run build` passou e o bundle foi auditado sem
    credenciais ou referências de infraestrutura. Os testes prioritários das T-025 a
    T-028 observaram os cinco eventos, TTL/DLX, retries/DLQ, reconexão, duas abas,
    deduplicação, toast/banner e mock Brevo sem rede externa. O build Angular emitiu
    apenas um warning de orçamento de CSS em `hub-page.scss`, sem falha.

## Emenda de provedor — 2026-10-29

A decisão de provedor foi alterada de Brevo para Web3Forms. As evidências das tarefas
anteriores que citam Brevo registram a implementação substituída e não representam o
provedor atual. A nova rodada deve preservar a porta provider-neutral, a topologia
RabbitMQ, retries/DLQ e os testes de isolamento do ciclo do documento.

- [x] T-034 [FR-014][BR-014 a BR-018] Substituir o adaptador de e-mail por Web3Forms.
  - Arquivos/módulos: `document/adapter/out/email/web3forms/`, configuração backend,
    `infra/.env.example`, testes HTTP locais e documentação operacional.
  - Verificação: o adaptador envia `access_key`, `subject`, `from_name`, `email`,
    `replyto` e `message` para o endpoint configurado; classifica 2xx/303 como sucesso,
    429/5xx como transitório e 4xx como permanente; nenhuma credencial chega ao
    frontend; o consumidor e as DLQs permanecem inalterados.
  - Evidência: `Web3FormsEmailSenderTest`, `DocumentEmailMessageConsumerTest` e a
    suíte de regressão backend passaram; nenhuma referência operacional à Brevo ficou
    no código ativo.

- [x] T-035 [FR-014][AC-010] Validar a troca sem provedor externo real.
  - Verificação: testes com servidor HTTP local, regressão backend/frontend e smoke
    local do Hub; registrar que o envio real server-side depende da liberação/plano da
    Web3Forms e de `WEB3FORMS_ACCESS_KEY` configurado.
  - Evidência: testes HTTP locais cobriram sucesso, 303, 400, 429, 503, resposta
    negativa, timeout e configuração ausente; backend e frontend responderam HTTP 200
    no ambiente local. Nenhum envio externo foi executado.

- [ ] T-033 [FR-001 a FR-014][AC-001 a AC-010] Revisar convergência e obter aprovação.
  - Arquivos/módulos: Spec, ADR, plano, tarefas, código, testes, documentação e rollback.
  - Verificação: cada FR/BR/AC aponta para código/teste, divergências estão registradas,
    Marcos aprovou e nenhum deploy foi executado nesta tarefa.

## Adiado

- [ ] Histórico persistido de notificações, leitura, arquivamento e contador de não lidas.
- [ ] Login, autenticação, autorização, multiusuário e roteamento por destinatário.
- [ ] Destinatários por documento/upload ou múltiplos endereços configuráveis.
- [ ] Troca da Brevo sem necessidade de alteração do contrato provider-neutral.
- [ ] Outbox transacional, migration de notificações ou replay de eventos.
- [ ] Push do sistema operacional, SMS, WhatsApp ou canais externos adicionais.
- [ ] Deploy/hospedagem externa e envio de e-mail real em testes automatizados.
