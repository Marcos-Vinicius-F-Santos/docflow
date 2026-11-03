# Plano de Implementação — Notificações de documentos

Spec relacionada: specs/03-features/notificacoes-documentos/spec.md  
ADR relacionada: specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md  
Status: Aprovado

## 1. Resumo técnico

A feature adicionará notificações de ciclo de vida sem alterar as transições de
documentos, o retry via staging ou a exclusão definitiva já aprovados. O backend
publicará eventos depois das transições persistidas e um serviço de notificações os
encaminhará por dois canais: broadcast WebSocket para todas as abas ativas do contexto
único da instalação e e-mail assíncrono para o destinatário configurado.

RabbitMQ continuará sendo o transporte interno. A topologia da ADR-008 será declarada
em document/messaging/rabbitmq/, com exchange de eventos, fila de notificações, fila
de espera do aviso de exclusão, fila de e-mail, retries 30s/5m/30m e DLQs. A integração
com a Web3Forms ocorrerá somente atrás de um adaptador de e-mail; o access key e
DOCFLOW_NOTIFICATION_DEFAULT_EMAIL virão de configuração de ambiente.

O frontend Angular conectará o Hub ao endpoint WebSocket, exibirá toast e banner
persistentemente durante a sessão, deduplicará por eventId, reconectará e sincronizará
GET /documents ao recuperar a conexão. Nenhuma credencial, fila, routing key, bucket ou
URL de infraestrutura será exposta ao navegador.

Não haverá migration ou tabela de histórico nesta rodada. O estado REST permanecerá a
fonte de verdade e eventos WebSocket perdidos durante uma desconexão não serão
reproduzidos; e-mails seguirão sua própria fila assíncrona.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| backend/.../document/application/ | Publicar eventos após transições e coordenar o despacho para WebSocket/e-mail. | Alto: evento antecipado ou duplicado pode informar estado incorreto. |
| backend/.../document/messaging/rabbitmq/ | Adicionar contratos, topologia, publishers, consumidores, retries e DLQs de notificações. | Alto: erro de ack, TTL ou binding pode perder ou duplicar eventos. |
| backend/.../document/adapter/in/websocket/ | Aceitar conexões do Hub e fazer broadcast dos eventos públicos. | Alto: reconexão e múltiplas abas podem gerar vazamento ou duplicação visual. |
| backend/.../document/adapter/out/email/ | Criar porta provider-neutral e adaptador Web3Forms. | Alto: credenciais, classificação de erros e quota externa. |
| backend/configuração e infra/ | Documentar DOCFLOW_NOTIFICATION_DEFAULT_EMAIL e WEB3FORMS_ACCESS_KEY sem versionar valores. | Alto: segredo no código ou envio para destinatário incorreto. |
| frontend/src/app/hub/ | Conectar WebSocket, renderizar toast/banner, reconectar e sincronizar listagem. | Médio: regressão no upload, listagem e ações do Hub. |
| frontend/src/app/notifications/ | Criar serviço/estado de eventos se houver conteúdo suficiente para justificar subpasta por domínio. | Médio: estado duplicado ou acoplamento excessivo ao componente Hub. |
| backend/src/test/ e frontend/src/ | Adicionar testes de contratos, RabbitMQ, WebSocket, Web3Forms e interface. | Médio: testes externos lentos ou não determinísticos. |
| specs/ e docs/ | Atualizar ADR, arquitetura, contratos e operação. | Baixo: divergência documental. |

Não será criado um banco de notificações, uma associação permanente entre documento e
usuário ou uma transação distribuída. Se a implementação exigir outbox, histórico,
autorização ou destinatário por upload, a tarefa deve parar e abrir nova decisão antes
de ampliar o schema ou o contrato.

## 3. Componentes novos

- Evento versionado de ciclo de vida com eventId, schemaVersion, type, documentId e
  occurredAt, com campos de status/contagem quando aplicável.
- Publisher de eventos de documento em document/messaging/rabbitmq/.
- Topologia de eventos e notificações conforme ADR-008:
  - exchange docflow.document-events;
  - fila docflow.document-notifications.requested;
  - fila docflow.document-notifications.delay.2h30;
  - fila docflow.document-email-notifications.requested;
  - filas de retry de e-mail .retry.30s, .retry.5m e .retry.30m;
  - DLX/DLQ de notificações e de e-mail.
- Consumidor de eventos para broadcast WebSocket e publicação de comandos de e-mail.
- Handler/adapter WebSocket para GET /ws/notifications por upgrade, sem STOMP.
- Porta de saída de e-mail e adaptador Web3Forms por API, com classificação de erros.
- Configuração segura de WEB3FORMS_ACCESS_KEY e DOCFLOW_NOTIFICATION_DEFAULT_EMAIL.
- Serviço Angular de conexão, reconexão, deduplicação por eventId e estado de banner.
- Componentes de apresentação de toast e banner no Hub, reutilizados por todas as abas.
- Testes unitários, de integração com Testcontainers e ponta a ponta dos cinco eventos.

## 4. Contratos e topologia

### 4.1 Eventos de ciclo de vida

Os tipos públicos são:

- DOCUMENT_PROCESSING_STARTED;
- DOCUMENT_COMPLETED;
- DOCUMENT_FAILED;
- DOCUMENT_DELETION_WARNING;
- DOCUMENT_DELETED.

O evento só será publicado depois da persistência que o justifica:

- processamento iniciado depois de PROCESSING;
- concluído depois de COMPLETED e confirmação do storage;
- falha depois de FAILED e atualização do contador;
- aviso depois da agenda de exclusão estar persistida;
- excluído depois do tratamento completo de conteúdo, staging e metadados.

O evento de falha de WebSocket ou e-mail não altera o documento. Todos os publishers
devem usar publisher confirm e os consumidores devem ser idempotentes.

### 4.2 WebSocket

- Endpoint: GET /ws/notifications por upgrade para WebSocket.
- O servidor fará broadcast para todas as conexões ativas do contexto único da
  instalação.
- O frontend usará WebSocket nativo e não conhecerá RabbitMQ.
- Ao reconectar, o Hub executará GET /documents; eventos antigos não serão reproduzidos.
- Cada aba terá toast e banner em sua própria sessão e fará deduplicação por eventId.

### 4.3 E-mail

- Destinatário: DOCFLOW_NOTIFICATION_DEFAULT_EMAIL.
- Provedor atual: Web3Forms por API, atrás de porta provider-neutral.
- Credencial: WEB3FORMS_ACCESS_KEY, somente em ambiente/segredo local ou de execução.
- Comando de e-mail: evento público, destinatário configurado e conteúdo renderizável;
  sem referências de storage ou credenciais.
- Falhas transitórias: tentativa inicial + retries em 30s, 5m e 30m.
- Falhas permanentes: DLQ direta; 429 é transitório, outros 4xx são permanentes.
- Falha de e-mail não altera status, failure_count, retry ou exclusão do documento.

### 4.4 Mensageria física aprovada

| Elemento | Nome |
|---|---|
| Exchange de eventos | docflow.document-events |
| Fila WebSocket | docflow.document-notifications.requested |
| Fila de espera do aviso | docflow.document-notifications.delay.2h30 |
| Fila de e-mail | docflow.document-email-notifications.requested |
| Routing key de e-mail | notification.email.requested |
| Retry e-mail | docflow.document-email-notifications.retry.30s / .retry.5m / .retry.30m |
| DLX WebSocket/notificações | docflow.document-notifications.dlx |
| DLQ WebSocket/notificações | docflow.document-notifications.dlq |
| DLX e-mail | docflow.document-email-notifications.dlx |
| DLQ e-mail | docflow.document-email-notifications.dlq |

O TTL da fila de espera do aviso continua em 2h30, para que o aviso aconteça 30 minutos
antes de exclusion_scheduled_at, cujo prazo total é de três horas.

## 5. Sequência de implementação

### Fase 1 — Base

1. Fechar a matriz FR/BR/AC da Spec para evento, WebSocket, e-mail, UI e reconexão.
2. Registrar no baseline o comportamento atual do Hub, processamento RabbitMQ, exclusão
   e ausência de canal WebSocket/e-mail.
3. Confirmar o contrato público JSON e os cinco tipos de evento sem expor nomes físicos.
4. Confirmar a topologia física da ADR-008, incluindo bindings, TTL 2h30, retries de
   e-mail, publisher confirm, ack/nack e DLQs.
5. Definir as variáveis de ambiente sem valores: BREVO_API_KEY e
   DOCFLOW_NOTIFICATION_DEFAULT_EMAIL.
6. Preparar fixtures, contratos e stubs de Brevo sem alterar o fluxo de documentos.

Gate da Fase 1: matriz, contrato JSON, topologia, configuração e estratégia de falhas
estão revisados; baseline existente continua passando.

### Fase 2 — Lógica principal

1. Implementar os modelos e mapeadores dos eventos de ciclo de vida.
2. Publicar PROCESSING_STARTED, COMPLETED e FAILED nos pontos corretos do fluxo de
   documento, sem alterar as transições existentes.
3. Publicar a mensagem de espera do aviso quando a terceira falha persistir a agenda;
   encaminhar o evento após TTL para DOCUMENT_DELETION_WARNING.
4. Publicar DOCUMENT_DELETED somente após exclusão automática ou manual concluída.
5. Declarar exchange, filas, bindings, TTL, DLX/DLQ e mensagens persistentes em
   document/messaging/rabbitmq/.
6. Implementar o consumidor de eventos com publisher confirm, ack após processamento e
   idempotência por eventId.
7. Implementar a porta de e-mail e o adaptador Brevo, incluindo classificação de 429/5xx
   como transitórios e 4xx permanentes.
8. Implementar filas 30s/5m/30m, limite de três retries e encaminhamento para DLQ.
9. Implementar o handler WebSocket, registro das conexões, broadcast para todas as abas
   e descarte controlado quando não houver conexão ativa.
10. Garantir que erro WebSocket/e-mail não altere o estado nem crie retry de documento.

Gate da Fase 2: os cinco eventos são produzidos nos pontos corretos; WebSocket e e-mail
recebem mensagens; retries/DLQs e falhas de canal são isolados do ciclo do documento.

### Fase 3 — Interface

1. Criar serviço Angular de notificações com conexão WebSocket nativa e reconexão
   progressiva.
2. Ao abrir/reconectar o Hub, consultar GET /documents e manter a lista como fonte de
   verdade.
3. Criar o estado de notificações da sessão com deduplicação por eventId e suporte a
   todas as abas conectadas.
4. Exibir toast temporário e banner persistente para cada evento válido.
5. Associar a notificação ao documento e oferecer navegação/atualização somente quando
   o documento ainda fizer sentido na listagem.
6. Exibir estados de conexão sem bloquear upload, retry, download ou exclusão.
7. Garantir que o frontend não contenha API key Brevo, endereço hardcoded, URL de
   RabbitMQ, MinIO ou PostgreSQL.

Gate da Fase 3: Hub conecta, recebe eventos, mostra toast/banner, reconecta, atualiza a
listagem e mantém os fluxos existentes funcionando em múltiplas abas.

### Fase 4 — Testes

1. Cobrir unitariamente contratos, mapeamento, tipos e regras de publicação.
2. Cobrir o produtor nos estados PROCESSING, COMPLETED, FAILED, terceira falha e
   exclusão manual/automática.
3. Cobrir topologia RabbitMQ com Testcontainers, bindings, persistência, TTL, ack/nack,
   publisher confirm, duplicidade e DLQs.
4. Cobrir o adaptador Brevo com servidor HTTP falso, 2xx, timeout, 429, 5xx, 4xx,
   credencial ausente e limite de três retries.
5. Cobrir WebSocket com conexão, broadcast para múltiplas abas, desconexão, reconexão,
   envio sem clientes e falha de entrega.
6. Cobrir o serviço Angular com eventos válidos, desconhecidos, duplicados e erros de
   conexão, verificando toast/banner e nova consulta REST.
7. Executar E2E com upload, início, conclusão, falha, aviso, exclusão e entrega de
   e-mail simulada, sem usar serviços externos reais na suíte.
8. Executar build e regressão completa do backend/frontend e smoke test local.

Gate da Fase 4: AC-001 a AC-010 passam; mensagens duplicadas não geram efeito indevido;
falhas de canal não alteram documentos; os fluxos atuais permanecem verdes.

### Fase 5 — Entrega

1. Atualizar docs de API, operação, ambiente, RabbitMQ, WebSocket, Brevo e DLQ.
2. Documentar BREVO_API_KEY e DOCFLOW_NOTIFICATION_DEFAULT_EMAIL como variáveis sem
   valores versionados, incluindo procedimento local de configuração.
3. Atualizar ARQUITETURA.md com a referência da ADR-008 e os limites de entrega.
4. Executar git diff --check, build, testes prioritários, regressão e inspeção de
   segredos/endpoints no frontend.
5. Executar smoke test controlado com Brevo mockado e confirmar que nenhum e-mail real
   será enviado sem configuração explícita.
6. Fazer a revisão de convergência Spec ↔ Plano/Tarefas ↔ Código ↔ Testes e obter a
   aprovação final de Marcos antes de qualquer deploy.

Gate da Fase 5: documentação, configuração, testes, rollback e evidências revisados;
nenhum deploy ocorre sem aprovação final.

## 6. Riscos

| Risco | Chance | Impacto | Mitigação | Merece ADR? |
|---|---|---|---|---|
| Estado persistido e publicação do evento não são uma transação única. | Alta | Alto | Publisher confirm, idempotência, reconciliação operacional e REST como fonte de verdade. | Já coberto pela ADR-008; nova decisão só se for criado outbox. |
| Evento duplicado ou fora de ordem. | Alta | Médio | eventId, deduplicação no frontend, consumidores idempotentes e estado REST. | Não; coberto pela Spec/ADR. |
| TTL de 2h30 não dispara exatamente por indisponibilidade do broker. | Média | Alto | Filas duráveis, timestamp persistido de exclusão, teste com TTL reduzido e recuperação operacional. | Já coberto pela ADR-007/008. |
| WebSocket desconectado perde notificação. | Alta | Médio | Reconexão, nova consulta GET /documents e explicitação de ausência de replay. | Já coberto pela ADR-008. |
| Broadcast global expõe eventos entre abas/instalações compartilhadas. | Média | Alto | Escopo explícito de instalação única; autenticação/multiusuário exigirão nova decisão. | Já coberto pela ADR-008. |
| Brevo indisponível, quota atingida ou marca gratuita indesejada. | Média | Médio | Adaptador provider-neutral, retries, DLQ, env vars e escolha reversível do provedor. | Não, salvo troca de contrato ou provedor. |
| API key ou destinatário vazam no frontend/repositório. | Média | Alto | Configuração backend-only, inspeção automatizada e valores fora do Git. | Não; regra de segurança existente. |
| Adição do canal de e-mail afeta o processamento. | Baixa | Alto | Fila separada, ack independente e falhas sem alteração do documento. | Já coberto pela ADR-008. |

## 7. Perguntas e decisões antes da implementação

- A Spec de notificações está aprovada e a ADR-008 está aceita.
- Brevo está aprovado como provedor inicial, aceitando a marca do plano gratuito.
- RabbitMQ permanece obrigatório como mensageria interna; Brevo não o substitui.
- O destinatário temporário será configurado por DOCFLOW_NOTIFICATION_DEFAULT_EMAIL.
- Nenhum valor de e-mail ou API key deve ser adicionado aos documentos versionados.
- O plano ainda precisa ser revisado e aprovado por Marcos antes da implementação.
- A implementação não deve criar histórico, outbox, login, associação por usuário ou
  migration sem nova decisão aprovada.
