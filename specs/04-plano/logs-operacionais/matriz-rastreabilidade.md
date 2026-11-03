# Matriz de rastreabilidade — Logs operacionais

| Requisito | Contrato/decisão | Implementação prevista | Teste previsto |
|---|---|---|---|
| FR-001, FR-003, FR-012 | Evento versionado, níveis INFO/WARN/ERROR e sanitização | `ApplicationLogEvent`, contexto e sanitizador | T-008, T-023 |
| FR-002 | Eventos do ciclo do documento e infraestrutura | Instrumentação de aplicação, consumers e adapters | T-010, T-024 |
| FR-004, FR-005, FR-007, FR-011 | RabbitMQ assíncrono, ACK após persistência e idempotência | Publisher, consumer, repository e topologia | T-011 a T-013, T-026 |
| FR-006 | Fallback local e isolamento do fluxo principal | Logger local e publisher tolerante a falha | T-009, T-016, T-026 |
| FR-008, FR-010 | `GET /logs`, filtros, paginação e ProblemDetail | Query service, controller e DTOs | T-015, T-027 |
| FR-009 | Rota read-only `/logs` | Página Angular, serviço e estados visuais | T-017 a T-022, T-028 |
| FR-011 | Retenção de três horas | Scheduler e repository de expiração | T-014, T-025 |
| BR-001 a BR-011 | ADR-009 e contrato do evento | Domínio de logs sem mutação de documentos | T-008 a T-016, T-023 a T-026 |
| AC-001 a AC-010 | Critérios da Spec §6 | Backend, frontend, Compose e documentação | T-023 a T-032 |

Limites confirmados: sem autenticação, sem histórico infinito, sem edição/exclusão pela
interface, sem tracing distribuído e sem microserviço separado nesta rodada.
