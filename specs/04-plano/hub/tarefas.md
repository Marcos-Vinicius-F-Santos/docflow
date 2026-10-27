# Tarefas — Hub de documentos

Plano relacionado: specs/04-plano/hub/plano.md

## Regra das tarefas

Cada tarefa deve ser executada na ordem indicada, ser revisável isoladamente, referenciar
os requisitos FR correspondentes e registrar como será verificada. Nenhuma tarefa de
implementação deve começar antes do gate da Fase 1 e da aprovação da Spec/Plano.

## Fase 1 — Base

- [ ] T-001 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Montar a matriz da Spec contra o comportamento atual, contratos, código e testes.
  - Arquivos/módulos: specs/03-features/hub/spec.md, frontend/src/app/, backend/.../document/ e testes existentes.
  - Verificação: a matriz registra o baseline de /, upload, listagem, acompanhamento, download e exclusão individual, além das lacunas de retry e exclusão atrasada.

- [ ] T-002 [FR-009][FR-010][FR-013][FR-014][FR-015] Criar e revisar ADR para retry manual via staging e fila de exclusão.
  - Arquivos/módulos: specs/02-arquitetura/DECISAO/, Document, consumidor RabbitMQ e política ADR-006.
  - Verificação: ADR aprovado descreve lock, falha inicial contando, limite 3, isExcluded, exclusionScheduledAt, TTL de 3 horas, exclusão completa, idempotência e corrida com exclusão manual.

- [ ] T-003 [FR-012][FR-015][FR-017] Fechar o contrato HTTP de POST /documents/{documentId}/retry.
  - Arquivos/módulos: specs/03-features/hub/spec.md, docs/api.md, DocumentController e ProblemDetail.
  - Verificação: contrato aprovado define 202, 404, 409, 503, corpo de sucesso, ausência de objectKey/credenciais e comportamento para documento PROCESSING ou isExcluded.

- [ ] T-004 [FR-010][FR-013][FR-014] Fechar o contrato da mensagem e da topologia da fila de exclusão com os nomes aprovados: exchange docflow.document-deletion, fila de espera docflow.document-deletion.delay.3h, fila consumida docflow.document-deletion.requested, routing keys document.deletion.scheduled/document.deletion.requested, DLX docflow.document-deletion.dlx e DLQ docflow.document-deletion.dlq.
  - Arquivos/módulos: ADR do Hub, document/messaging/rabbitmq/ e configuração RabbitMQ.
  - Verificação: exchange, routing key, fila durável, TTL de 3 horas, persistência, DLQ, confirmação de publicação e consumo idempotente estão definidos.

- [ ] T-005 [FR-010][FR-011][FR-013][FR-014] Preparar a migration expand-only com failure_count, is_excluded e exclusion_scheduled_at e os fixtures de persistência.
  - Arquivos/módulos: backend/src/main/resources/db/migration/, testes PostgreSQL e docs/database.md.
  - Verificação: a migration adiciona falhas, isExcluded e exclusionScheduledAt sem editar V1–V5 e define valores seguros para documentos existentes.

- [ ] T-006 [FR-005][FR-006][FR-007][FR-008][FR-016] Registrar testes de baseline antes da lógica nova.
  - Arquivos/módulos: testes backend e frontend existentes.
  - Verificação: upload, consulta, listagem, download, exclusão individual e rotas atuais passam antes das alterações.

**Gate da Fase 1:** Spec, ADR, contratos HTTP/mensageria, migration e baseline foram
revisados; Marcos aprovou o início da implementação.

## Fase 2 — Lógica principal

- [ ] T-007 [FR-010][FR-011][FR-013][FR-014] Adicionar ao domínio Document os atributos e transições de falhas/exclusão.
  - Arquivos/módulos: backend/src/main/java/com/dockflow/dockflow/document/Document.java e DocumentStatus.java.
  - Verificação: testes unitários confirmam falhas inicial em 0, incremento em falha, reset em sucesso, isExcluded em false e marcação na terceira falha.

- [ ] T-008 [FR-012][FR-013][FR-014][FR-015] Implementar locks e consultas de elegibilidade no repositório.
  - Arquivos/módulos: DocumentRepository.java e testes PostgreSQL.
  - Verificação: retry concorrente só obtém um lock; documentos PROCESSING, isExcluded ou sem staging não são elegíveis; exclusão agendada pode ser recuperada.

- [ ] T-009 [FR-010][FR-011][FR-013] Aplicar a migration e o mapeamento JPA de failure_count, is_excluded e exclusion_scheduled_at.
  - Arquivos/módulos: migration V6 ou próxima, Document.java e testes de contexto.
  - Verificação: Flyway aplica a migration após V5, registros existentes permanecem legíveis e defaults são persistidos corretamente.

- [ ] T-010 [FR-012][FR-015][FR-016] Implementar o caso de uso de retry com lock, staging existente e publisher confirm.
  - Arquivos/módulos: document/application/, DocumentService ou caso de uso equivalente, DocumentStorageRequestPublisher e repositório.
  - Verificação: somente FAILED com falhas < 3, isExcluded false e staging existente passa para PROCESSING e publica uma nova solicitação; falha de publicação preserva o estado seguro.

- [ ] T-011 [FR-012][FR-015][FR-017] Expor POST /documents/{documentId}/retry e seus erros HTTP.
  - Arquivos/módulos: DocumentController, DTOs, GlobalExceptionHandler e testes MVC.
  - Verificação: 202, 404, 409 e 503 coincidem com o contrato e nenhuma resposta expõe bucket, objectKey ou credenciais.

- [ ] T-012 [FR-010][FR-011][FR-013] Atualizar o consumidor de armazenamento para persistir a contagem de falhas.
  - Arquivos/módulos: DocumentStorageMessageConsumer, RabbitMqDocumentStorageMessageHandler e Document.
  - Verificação: falha inicial e falhas posteriores incrementam falhas; sucesso deixa COMPLETED/falhas 0; a terceira falha marca FAILED/isExcluded.

- [ ] T-013 [FR-013][FR-014] Implementar publisher, exchange docflow.document-deletion, filas delay.3h/requested, routing keys aprovadas, DLX, DLQ e TTL da exclusão.
  - Arquivos/módulos: document/messaging/rabbitmq/ e DocumentStorageRabbitConfiguration ou configuração equivalente.
  - Verificação: mensagem persistente contém somente a correlação necessária, o TTL de 3 horas está declarado e publisher confirm trata falhas sem ack prematuro.

- [ ] T-014 [FR-013][FR-014][FR-016] Implementar consumidor idempotente da fila de exclusão.
  - Arquivos/módulos: novo consumidor RabbitMQ, DocumentStorage, DocumentStagingRemover e DocumentRepository.
  - Verificação: após o prazo remove binário, staging e metadados; documento já removido manualmente gera ack idempotente; falhas de storage não são reportadas como sucesso.

- [ ] T-015 [FR-006][FR-007][FR-008][FR-009][FR-012][FR-015] Atualizar modelos e serviço HTTP Angular para os campos e retry.
  - Arquivos/módulos: frontend/src/app/document/document.models.ts e document-api.service.ts.
  - Verificação: o cliente envia a rota/verb/body corretos, interpreta 202 e erros, e não acessa infraestrutura diretamente.

**Gate da Fase 2:** backend e serviço HTTP exercitam retry, contagem, limite, fila de
exclusão e exclusão manual; os contratos existentes permanecem verdes.

## Fase 3 — Interface

- [ ] T-016 [FR-001][FR-002][FR-016][FR-017] Criar a página Hub e trocar o destino da rota /.
  - Arquivos/módulos: frontend/src/app/hub/, app.routes.ts e app shell.
  - Verificação: / abre o Hub; upload e listagem ocorrem no Hub; um item do Hub navega para /documents/:documentId.

- [ ] T-017 [FR-003][FR-006] Implementar a faixa lateral sem seção própria de downloads.
  - Arquivos/módulos: página/template/estilos do Hub e DocumentApiService.
  - Verificação: a coluna lateral exibe até cinco documentos em Atividade, até cinco FAILED recentes em Atenção e nenhuma seção exclusiva de downloads.

- [ ] T-018 [FR-004][FR-005][FR-010] Integrar o dropbox ao validador e ao upload multipart existentes.
  - Arquivos/módulos: Hub, DocumentFileValidator, DocumentApiService e componentes de upload existentes.
  - Verificação: arrastar/selecionar arquivo inicia uma única operação multipart, preserva validações e mantém o usuário no Hub após sucesso.

- [ ] T-019 [FR-006][FR-008][FR-016] Implementar a lista central de todos os documentos e seus estados de carregamento, vazio e erro.
  - Arquivos/módulos: página/template/estilos do Hub, modelos e mapeador de erros.
  - Verificação: GET /documents é chamado uma vez por carregamento, lista os quatro estados e oferece nova tentativa sem mostrar dados obsoletos como atuais.

- [ ] T-020 [FR-007][FR-008][FR-009] Implementar os indicadores visuais dos estados.
  - Arquivos/módulos: componentes/template/estilos do Hub.
  - Verificação: COMPLETED usa check verde, PENDING/PROCESSING usa refresh azul e FAILED usa X vermelho.

- [ ] T-021 [FR-007][FR-009][FR-012][FR-014][FR-015][FR-016] Integrar download, retry elegível e exclusão manual nas ações dos documentos.
  - Arquivos/módulos: Hub, DocumentApiService, mapeador de erros e rotas existentes.
  - Verificação: download só é oferecido para COMPLETED, retry chama a API aprovada, retry bloqueia falhas >= 3/isExcluded e exclusão manual permanece confirmada.

- [ ] T-022 [FR-003][FR-004][FR-006][FR-016] Revisar acessibilidade, foco, teclado, responsividade e estados concorrentes.
  - Arquivos/módulos: templates, estilos e testes do Hub.
  - Verificação: ações são operáveis por teclado, feedbacks têm semântica acessível e upload/retry/download/exclusão não podem ser disparados concorrentemente de forma inválida.

**Gate da Fase 3:** Hub visual e funcional, rota inicial alterada, upload permanece no
Hub, estados e ações estão corretos e rotas antigas não regrediram.

## Fase 4 — Testes

- [ ] T-023 [FR-010][FR-011][FR-013][FR-014] Cobrir o domínio e a migration.
  - Arquivos/módulos: testes de Document, repository, contexto JPA e migration.
  - Verificação: inicialização, incrementos, reset, terceira falha, isExcluded, timestamp e defaults de dados existentes estão cobertos.

- [ ] T-024 [FR-012][FR-015][FR-017] Cobrir o endpoint e o serviço de retry.
  - Arquivos/módulos: testes de DocumentService/caso de uso, DocumentController e GlobalExceptionHandler.
  - Verificação: 202/404/409/503, lock, staging ausente, publicação não confirmada, PROCESSING concorrente e ausência de vazamento de infraestrutura.

- [ ] T-025 [FR-013][FR-014][FR-016] Cobrir a topologia e o consumidor da exclusão com RabbitMQ real.
  - Arquivos/módulos: testes RabbitMQ/Testcontainers, configuração e consumidor de exclusão.
  - Verificação: mensagem persistente, TTL configurado, ack/nack, exclusão completa, documento ausente e falha de storage são verificáveis; TTL de produção permanece 3 horas, com redução somente no ambiente de teste.

- [ ] T-026 [FR-010][FR-011][FR-013][FR-014] Cobrir a corrida entre retry, consumidor, exclusão manual e exclusão atrasada.
  - Arquivos/módulos: testes de integração PostgreSQL, RabbitMQ e MinIO.
  - Verificação: não há dupla publicação efetiva, nenhum documento é recriado, mensagens tardias são idempotentes e os objetos corretos são removidos.

- [ ] T-027 [FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-012][FR-015] Cobrir o serviço Angular.
  - Arquivos/módulos: document-api.service.spec.ts, modelos e mapeador de erros.
  - Verificação: upload, listagem, download, retry, exclusão, corpos, rotas, response types e erros coincidem com os contratos.

- [ ] T-028 [FR-001][FR-002][FR-003][FR-004][FR-006][FR-007][FR-008][FR-009][FR-016][FR-017] Cobrir o componente Hub.
  - Arquivos/módulos: specs da página Hub e componentes auxiliares.
  - Verificação: caminho feliz, lista vazia, falha de API, cinco recentes, cinco FAILED recentes, quatro estados, retry elegível/inelegível, permanência após upload e acompanhamento a partir do Hub.

- [ ] T-029 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Executar regressão ponta a ponta.
  - Arquivos/módulos: backend, frontend, PostgreSQL, RabbitMQ, MinIO, Compose e documentação de verificação.
  - Verificação: upload pelo Hub, conclusão, falha, retry, terceira falha, exclusão manual, exclusão atrasada e operações individuais passam sem regressão óbvia.

**Gate da Fase 4:** AC-001 a AC-009 passam com evidência automatizada ou de integração,
migration e fila são verificadas e a regressão completa permanece verde.

## Fase 5 — Entrega

- [ ] T-030 [FR-003][FR-009][FR-010][FR-013][FR-014][FR-015] Atualizar documentação de API, banco, operação e rollback.
  - Arquivos/módulos: docs/api.md, docs/database.md, docs/document-registration-operations.md, README.md, ADR e specs/06-deploy/.
  - Verificação: outra pessoa consegue entender retry, contador, marcação, TTL, exclusão completa e rollback sem depender de conhecimento implícito.

- [ ] T-031 [FR-001][FR-004][FR-005][FR-006][FR-015][FR-016] Executar build, testes prioritários e inspeção de segurança.
  - Arquivos/módulos: backend/, frontend/, configuração de ambiente e artefatos de build.
  - Verificação: builds/testes passam; frontend não contém credenciais, objectKey, URLs de MinIO/RabbitMQ ou conexões de banco.

- [ ] T-032 [FR-001][FR-003][FR-004][FR-006][FR-007][FR-008][FR-009][FR-010][FR-012][FR-013][FR-014][FR-015][FR-016] Executar smoke test local e registrar evidências.
  - Arquivos/módulos: Compose, backend, frontend, PostgreSQL, RabbitMQ, MinIO e checklist de verificação.
  - Verificação: rota inicial, upload/listagem no Hub, estados, falha inicial, retry, terceira falha, fila de exclusão, exclusão manual e acompanhamento individual são observados.

- [ ] T-034 [FR-018][FR-019] Implementar blocos compactos, navegação para acompanhamento e preview PDF.
  - Arquivos/módulos: frontend/src/app/hub/, frontend/src/app/document/pages/document-detail-page.*, testes do Hub e da página de acompanhamento, documentação de frontend e spec da feature.
  - Verificação: nome truncado com tooltip completo, clique navega para /documents/:documentId, metadados/status e download, retry elegível, exclusão e preview PDF são exibidos conforme o estado.

- [ ] T-033 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016][FR-017] Executar revisão final de convergência e obter aprovação.
  - Arquivos/módulos: Spec, plano, tarefas, ADR, migration, código, testes, documentação e rollback.
  - Verificação: cada FR/AC aponta para implementação e teste, divergências estão registradas, Marcos aprovou e nenhum deploy foi executado nesta tarefa.

## Adiado (fora do escopo desta rodada)

- [ ] Busca, filtros, paginação e ordenação avançada da listagem.
- [ ] Download em lote e exclusão em lote.
- [ ] Preview de formatos que não sejam PDF, visualização inline de outros conteúdos, Range ou retomada de download.
- [ ] Login, autenticação, autorização, multi-tenant ou associação de documentos a usuário.
- [ ] Retry automático, reprocessamento silencioso ou criação automática de novo arquivo.
- [ ] Alteração da política de reconciliação RESULT_UNKNOWN ou dos retries internos do fluxo existente.
- [ ] Deploy ou hospedagem externa sem aprovação final.

