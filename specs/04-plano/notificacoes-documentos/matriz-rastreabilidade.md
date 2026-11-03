# Matriz de rastreabilidade — Notificações de documentos

Tarefa: T-001  
Status: concluída em 2026-10-29  
Spec: `specs/03-features/notificacoes-documentos/spec.md`  
Plano: `specs/04-plano/notificacoes-documentos/plano.md`  
ADR: `specs/02-arquitetura/DECISAO/ADR-008-notificacoes-rabbitmq-websocket.md`

## 1. Escopo e baseline

Esta matriz é o registro da T-001. Ela liga cada requisito funcional, regra de
negócio e critério de aceite a um contrato, módulo e verificação planejada. Os
números de tarefa usados na coluna de teste apontam para as tarefas que deverão
produzir a evidência executável; eles não significam que a implementação já foi
realizada.

Baseline observado antes da feature:

| Área | Evidência existente | Situação para a feature |
|---|---|---|
| Estado do documento | `backend/src/main/java/com/dockflow/dockflow/document/Document.java`, `DocumentStatus.java` | Já existem `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`, contador de falhas e agenda de exclusão. As transições não serão alteradas pela T-001. |
| Início/conclusão/falha | `DocumentStorageMessageConsumer.java`, `DocumentService.java` e testes de aplicação/mensageria | Há pontos de processamento e persistência que serão instrumentados somente nas T-008 a T-010. |
| Reconciliação/exclusão | `DocumentReconciliationService.java`, `DocumentReconciliationScheduler.java`, `DocumentDeletionMessageConsumer.java` e `DocumentService.delete` | Há fluxos separados para agenda, exclusão automática e manual; a emissão posterior será tratada nas T-011 e T-012. |
| RabbitMQ | `document/messaging/rabbitmq/DocumentStorageRabbitTopology.java` e publishers/consumidores existentes | A topologia de storage existente será preservada. A nova topologia de notificações será declarada conforme ADR-008 nas T-004 e T-013. |
| Hub/frontend | `frontend/src/app/hub/`, `frontend/src/app/document/` e specs Angular existentes | O Hub consulta a API REST, mas ainda não possui WebSocket, toast, banner, deduplicação ou estado de conexão. |
| E-mail | Nenhum adaptador de notificação de documentos no backend | Será criado atrás de porta provider-neutral nas T-015 e T-016, sem segredo no frontend. |
| Persistência | Não há tabela/histórico de notificações | Não será criada migration, outbox, histórico, login, autorização ou vínculo persistido por usuário nesta feature. |

## 2. Requisitos funcionais

| Requisito | Contrato/decisão | Módulo responsável | Teste planejado |
|---|---|---|---|
| FR-001 — conectar o Hub ao abrir | Spec §3 FR-001; ADR-008 §1 e §3 | `frontend/src/app/notifications/` e `frontend/src/app/hub/`; endpoint `/ws/notifications` | T-018, T-027 e T-028 |
| FR-002 — expor somente contrato ao navegador | Spec §3 FR-002; Spec §5; ADR-008 §1 e §3 | DTO/evento público em `backend/.../document/messaging/rabbitmq/`; serviço Angular | T-003, T-007, T-018 e T-031 |
| FR-003 — notificar processamento iniciado | Spec §3 FR-003; BR-003; ADR-008 §3 | ponto de transição em `DocumentStorageMessageConsumer`/aplicação e publisher de eventos | T-008 e T-024 |
| FR-004 — notificar conclusão confirmada | Spec §3 FR-004; BR-004; ADR-008 §3 | consumidor de storage, `Document` e publisher | T-009 e T-024 |
| FR-005 — notificar falha efetiva | Spec §3 FR-005; BR-005/006; ADR-008 §3 | tratamento de falha do consumidor e publisher | T-010 e T-024 |
| FR-006 — agendar aviso na terceira falha | Spec §3 FR-006; BR-007; ADR-008 §2/§3 | agenda de exclusão, fila delay e publisher | T-011, T-024 e T-025 |
| FR-007 — entregar aviso 30 minutos antes | Spec §3 FR-007; BR-007; ADR-008 §2 | consumidor da fila delay e mapeador de evento | T-011, T-024 e T-025 |
| FR-008 — notificar exclusão concluída | Spec §3 FR-008; BR-008; ADR-008 §3 | exclusão automática/manual e publisher | T-012 e T-024 |
| FR-009 — toast e banner persistente | Spec §3 FR-009; BR-012; ADR-008 §5 | estado/componentes de notificações no Hub | T-021, T-027 e T-028 |
| FR-010 — sincronizar antes de agir sobre item ausente | Spec §3 FR-010; BR-002; ADR-008 §1/§4 | Hub, `DocumentApiService` e roteamento para documento | T-019, T-022 e T-028 |
| FR-011 — reconectar e consultar REST | Spec §3 FR-011; BR-010; ADR-008 §4 | serviço WebSocket e ciclo de vida do Hub | T-018, T-019, T-027 e T-028 |
| FR-012 — deduplicar por `eventId` | Spec §3 FR-012; BR-009/013; ADR-008 §4/§5 | estado de sessão do serviço Angular | T-020, T-027 e T-028 |
| FR-013 — isolar falha de entrega | Spec §3 FR-013; BR-005/010/014; ADR-008 §4 | consumidor de notificações, broadcast e consumidor de e-mail | T-013, T-014, T-016, T-017, T-025 e T-026 |
| FR-014 — encaminhar também por e-mail | Spec §3 FR-014; BR-014/015/016; ADR-008 §1/§2/§4 | fila de e-mail, porta e adaptador Web3Forms | T-015, T-016, T-024, T-026 e T-034 |

## 3. Regras de negócio

| Regra | Contrato/decisão | Módulo responsável | Teste planejado |
|---|---|---|---|
| BR-001 — RabbitMQ somente interno | Spec §4 BR-001; ADR-008 §1 | topologia/publishers backend e contrato público | T-003, T-004, T-007, T-025 e T-031 |
| BR-002 — REST é fonte de verdade | Spec §4 BR-002; ADR-008 §1/§4 | Hub, `DocumentApiService` e serviço WebSocket | T-019, T-022, T-027 e T-028 |
| BR-003 — iniciado após PROCESSING | Spec §4 BR-003; ADR-008 §3 | transição de aplicação/consumer e publisher | T-008 e T-024 |
| BR-004 — concluído após storage e persistência | Spec §4 BR-004; ADR-008 §3 | consumer de storage e publisher | T-009 e T-024 |
| BR-005 — falha de canal não é falha do documento | Spec §4 BR-005; ADR-008 §4 | consumidores de notificação e ciclo do documento | T-010, T-017, T-025, T-026 e T-029 |
| BR-006 — falha pode informar contagem sem detalhes internos | Spec §4 BR-006; Spec §5 | mapper/DTO público de falha | T-003, T-007 e T-024 |
| BR-007 — aviso somente com exclusão agendada | Spec §4 BR-007; ADR-008 §2/§3 | agenda, fila delay e consumer | T-011, T-024 e T-025 |
| BR-008 — excluído após tratar todos os recursos | Spec §4 BR-008; ADR-008 §3 | exclusão manual/automática e publisher | T-012, T-024 e T-029 |
| BR-009 — `eventId`, schema, tipo, documento e instante; pelo menos uma vez | Spec §4 BR-009; Spec §5; ADR-008 §3/§4 | contrato versionado, publisher, consumer e estado Angular | T-003, T-007, T-013, T-020, T-024, T-025 e T-028 |
| BR-010 — ciclo independente do WebSocket | Spec §4 BR-010; ADR-008 §4 | ciclo de documento separado dos adapters de entrega | T-013, T-014, T-017, T-027 e T-029 |
| BR-011 — broadcast no contexto único sem autenticação | Spec §4 BR-011; ADR-008 §5 | registro/broadcast WebSocket | T-014, T-027 e T-029 |
| BR-012 — toast/banner sem histórico persistido | Spec §4 BR-012; ADR-008 §4/§5 | estado de sessão do Hub | T-020, T-021, T-028 e T-030 |
| BR-013 — todas as abas recebem o evento | Spec §4 BR-013; ADR-008 §5 | broadcaster e conexão Angular por aba | T-014, T-020, T-023 e T-027 |
| BR-014 — e-mail desacoplado do processamento | Spec §4 BR-014; ADR-008 §1/§4 | fila/consumer de e-mail e porta | T-015, T-016, T-017, T-026 e T-029 |
| BR-015 — destinatário só por configuração segura | Spec §4 BR-015; ADR-008 §2 | configuração backend `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` | T-005, T-015, T-026 e T-031 |
| BR-016 — adaptador provider-neutral usando Web3Forms atualmente | Spec §4 BR-016; ADR-008 §2 | `document/port/out/email/` e `document/adapter/out/email/web3forms/` | T-005, T-015, T-026, T-031 e T-034 |
| BR-017 — timeout/5xx/429 com três retries e DLQ | Spec §4 BR-017; ADR-008 §4 | filas `.retry.30s`, `.retry.5m`, `.retry.30m`, consumer e DLQ | T-004, T-013, T-016, T-025, T-026 e T-032 |
| BR-018 — falhas permanentes direto à DLQ | Spec §4 BR-018; ADR-008 §4 | classificador Web3Forms, consumer e DLQ de e-mail | T-015, T-016, T-026, T-032 e T-034 |

## 4. Critérios de aceite

| Aceite | Contrato/decisão | Módulo responsável | Teste planejado |
|---|---|---|---|
| AC-001 — processamento iniciado | Spec §6 AC-001; FR-003; BR-003 | publisher após transição PROCESSING e Hub | T-008, T-024 e T-029 |
| AC-002 — processamento concluído | Spec §6 AC-002; FR-004; BR-004 | publisher após storage confirmado e sincronização REST | T-009, T-019, T-024 e T-029 |
| AC-003 — falha de processamento | Spec §6 AC-003; FR-005; BR-005/006 | evento FAILED com contador e apresentação no Hub | T-010, T-021, T-024 e T-029 |
| AC-004 — aviso de exclusão | Spec §6 AC-004; FR-006/007; BR-007 | fila delay e evento de aviso sem exclusão antecipada | T-011, T-025 e T-029 |
| AC-005 — exclusão concluída | Spec §6 AC-005; FR-008; BR-008 | publisher pós-exclusão e atualização da lista | T-012, T-022, T-024 e T-029 |
| AC-006 — reconexão | Spec §6 AC-006; FR-011; BR-002/010 | serviço WebSocket e consulta `GET /documents` | T-018, T-019, T-027 e T-029 |
| AC-007 — duplicidade | Spec §6 AC-007; FR-012; BR-009 | deduplicação por `eventId` no estado de sessão | T-020, T-027 e T-028 |
| AC-008 — falha do canal | Spec §6 AC-008; FR-013; BR-005/010/014 | isolamento dos consumers e adapters | T-013, T-014, T-017, T-025, T-026 e T-029 |
| AC-009 — toast/banner em múltiplas abas | Spec §6 AC-009; FR-009/012; BR-012/013 | componentes de apresentação e estado por aba | T-020, T-021, T-023, T-027 e T-028 |
| AC-010 — e-mail | Spec §6 AC-010; FR-014; BR-014/017/018 | fila, Web3Forms, retries e DLQ sem mutar documento | T-015, T-016, T-026, T-029, T-032, T-034 e T-035 |

## 5. Limites confirmados para evitar inferência

- Não há autenticação, autorização, multiusuário ou roteamento por destinatário.
- Não há histórico persistido, leitura, arquivamento ou contador de não lidas.
- Não há migration, tabela de notificações ou outbox nesta feature.
- Não há replay de eventos WebSocket perdidos durante desconexão.
- RabbitMQ permanece interno; o navegador não conhece exchange, fila, routing key,
  storage, MinIO, PostgreSQL, Web3Forms ou qualquer credencial.
- O e-mail usa `DOCFLOW_NOTIFICATION_DEFAULT_EMAIL` e `WEB3FORMS_ACCESS_KEY` somente no
  backend/ambiente, sem valores versionados.

## 6. Verificação da T-001

- Cada FR-001 a FR-014, BR-001 a BR-018 e AC-001 a AC-010 possui contrato, módulo e
  teste planejado nas tabelas acima.
- O baseline foi conferido contra o código e testes existentes antes de qualquer
  mudança de produção.
- Os limites de escopo foram registrados explicitamente; nenhum requisito de
  autenticação, histórico, outbox ou migration foi inferido.
- O gate da Fase 1 continua pendente até T-002 a T-006 serem executadas e revisadas.
- A primeira rodada foi bloqueada por restrições do executor: `npm.ps1` foi bloqueado pela
  Execution Policy e o acesso ao named pipe do Docker foi negado pelo sandbox. A rodada
  corretiva usou `npm.cmd` e execução com acesso controlado ao Docker/filesystem; o
  baseline foi então validado com 85 testes frontend e 27 testes backend prioritários,
  todos aprovados.
