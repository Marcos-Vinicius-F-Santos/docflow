# Tarefas — Contrato de mensageria RabbitMQ para o fluxo assíncrono de documentos

Plano relacionado: specs/04-plano/contrato-mensageria-rabbitmq-documentos/plano.md

Documento histórico: esta rodada foi consolidada em
`specs/03-features/registro-binario-e-armazenamento-assincrono-documentos/spec.md`.
Não usar estas tarefas como plano de execução da feature consolidada.

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente pra revisar de uma vez, referenciar o
requisito (FR-XXX) que ela implementa, e dizer como verificar que ficou pronta.

## Fase 1 — Base

- [ ] T-001 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006] Registrar as decisões já confirmadas: JSON v1, `DocumentStorageRequested`, seis campos, `contentReference`, exchange `direct` durável, fila durável, mensagens persistentes, routing key, publisher confirms, ausência de outbox, três retries além da entrega inicial nas filas de `5s`, `15s` e `60s`, DLQ, idempotência, filas de prioridade, logs estruturados e configuração MinIO por `MinEndpoint`/`MinSecurity`.
  - Arquivos/módulos: Spec da feature; `specs/02-arquitetura/DECISAO/` se a escolha sobre leitura do staging ou concorrência alterar um limite arquitetural.
  - Verificação: revisão documental confirma que as decisões do solicitante aparecem na Spec/plano e que não há conflito com ADR-001, ADR-002 ou ADR-003.

- [ ] T-002 [FR-001][FR-002][FR-003][FR-004][FR-006] Confirmar a compatibilidade da ingestão/staging MinIO já existente e fechar os detalhes ainda bloqueadores: timeout do publisher confirm, nomes da DLX/DLQ, filas de retry, formato de `contentReference`, operação de leitura, remoção imediata quando `exists` já for verdadeiro e encaminhamento para filas de prioridade em caso de concorrência.
  - Arquivos/módulos: `DocumentStaging`, `MinioDocumentStaging`, Spec da feature e eventual Registro de Decisão em `specs/02-arquitetura/DECISAO/`.
  - Verificação: a etapa anterior fornece um `contentReference` recuperável; cada detalhe bloqueador tem resposta aprovada e registrada; se uma resposta exigir outbox, estado persistido, lock distribuído ou alteração de porta, a implementação é separada em Spec/ADR antes de continuar.

- [ ] T-003 [FR-001][FR-003] Mapear o ponto de entrada do Application Service e preparar a estrutura específica em `document/messaging/rabbitmq`, sem criar módulo ou raiz genérica de mensageria.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/application/` e `backend/src/main/java/com/dockflow/dockflow/document/messaging/rabbitmq/`, conforme a organização-alvo.
  - Verificação: revisão estrutural confirma que publisher, consumidor e contrato ficam dentro do domínio `document`, sem dependência da API REST ou de SDK externo na lógica de aplicação.

- [ ] T-004 [FR-002][FR-003] Definir a forma provider-neutral de ler `contentReference` do staging, avaliando a extensão da porta `DocumentStorage` ou um resolvedor de conteúdo específico.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java`, adaptador MinIO e limite de mensageria.
  - Verificação: a decisão aprovada descreve assinatura, falhas e ownership da leitura; teste estrutural confirma que nenhuma classe de aplicação importa cliente MinIO diretamente.

- [ ] T-005 [FR-001][FR-005][FR-006] Preparar dependências e configuração externa do RabbitMQ e MinIO, incluindo exchange, fila, persistência, publisher confirms, filas de retry, DLX/DLQ, filas de prioridade, `MinEndpoint` e `MinSecurity` conforme as decisões aprovadas.
  - Arquivos/módulos: `backend/pom.xml`, `backend/src/main/resources/application.properties`, `infra/.env.example`, `infra/docker-compose.yml` e configuração de testes.
  - Verificação: aplicação e suíte existente continuam iniciando sem segredos hardcoded; os parâmetros vêm do ambiente e o broker isolado pode ser iniciado com a topologia aprovada.

## Fase 2 — Lógica principal

- [ ] T-006 [FR-002] Implementar o modelo JSON `DocumentStorageRequested` v1 com `schemaVersion`, `documentId`, `objectKey`, `contentReference`, `sizeBytes` e `contentType`.
  - Arquivos/módulos: `document/messaging/rabbitmq` e testes do contrato.
  - Verificação: teste de contrato serializa a mensagem esperada, preserva `schemaVersion = 1` e rejeita campos obrigatórios ausentes ou inválidos.

- [ ] T-007 [FR-001][FR-002] Implementar o publisher específico da feature, convertendo a solicitação do Application Service para JSON e publicando com a routing key `document.storage.requested`.
  - Arquivos/módulos: `document/application` e `document/messaging/rabbitmq`.
  - Verificação: teste unitário verifica que uma solicitação produz uma mensagem com os seis campos e a routing key aprovados.

- [ ] T-008 [FR-005] Implementar publisher confirms síncronos e propagação de falha, tratando ausência de confirmação, timeout ou rejeição como falha sem retornar aceitação assíncrona.
  - Arquivos/módulos: publisher, abstração de publicação e Application Service.
  - Verificação: testes simulam confirmação, indisponibilidade, rejeição e timeout; somente a confirmação recebida gera resultado de aceitação.

- [ ] T-009 [FR-002][FR-003][FR-006] Implementar o consumidor com validação do JSON v1 e encaminhamento direto de mensagens inválidas para a DLQ, sem retry e sem chamada a `DocumentStorage.store`.
  - Arquivos/módulos: `document/messaging/rabbitmq` e configuração de DLX/DLQ.
  - Verificação: teste publica mensagem sem cada campo obrigatório e confirma ausência de chamada ao storage e encaminhamento direto à DLQ.

- [ ] T-010 [FR-003][FR-004] Implementar a checagem de idempotência por `DocumentStorage.exists(objectKey)` antes da gravação e remover imediatamente o staging quando o objeto final já existir.
  - Arquivos/módulos: consumidor, `DocumentStorage` existente e testes unitários.
  - Verificação: quando `exists` retorna verdadeiro, o consumidor remove o staging, não chama `store` e conclui o processamento conforme a política aprovada; quando retorna falso, prossegue para leitura e gravação.

- [ ] T-011 [FR-003][FR-004] Implementar a leitura de `contentReference`, chamada de `DocumentStorage.store` e remoção do staging somente após confirmação do armazenamento final.
  - Arquivos/módulos: consumidor, operação provider-neutral de leitura aprovada, `DocumentStorage.delete` e adaptador correspondente.
  - Verificação: teste confirma a ordem leitura → `store` → `delete` → acknowledgment; falha em `store` não chama `delete`.

- [ ] T-012 [FR-004][FR-006] Implementar acknowledgment após sucesso do storage e tratamento distinto para falha transitória, falha não recuperável e `RESULT_UNKNOWN`, sem alterar o estado do documento nesta feature.
  - Arquivos/módulos: consumidor, configuração de acknowledgment e `DocumentStorageException`.
  - Verificação: `UNAVAILABLE` segue retry; `REJECTED` não é retentado; `RESULT_UNKNOWN` não é convertido em sucesso; nenhum cenário confirma antes do processamento permitido.

- [ ] T-013 [FR-006] Implementar três retries além da entrega inicial, usando filas separadas com atrasos de `5s`, `15s` e `60s` para falhas transitórias, e encaminhamento à DLQ após o limite.
  - Arquivos/módulos: configuração do consumidor, DLX/DLQ e política de retry.
  - Verificação: teste de integração conta as tentativas, valida os atrasos/configuração aprovados e confirma encaminhamento à DLQ sem loop infinito.

- [ ] T-014 [FR-001][FR-003][FR-005][FR-006] Emitir logs estruturados em publicação, início do consumo, sucesso e falha, sempre com `documentId` como correlation ID.
  - Arquivos/módulos: publisher, consumidor e configuração de logging.
  - Verificação: testes ou inspeção de eventos de teste confirmam os quatro pontos de log e a presença do `documentId`, sem incluir segredos ou binário.

## Fase 3 — Interface

- [ ] T-015 [FR-001][FR-003] Integrar publisher e consumidor aos adaptadores RabbitMQ usando exchange `direct` durável, fila durável, mensagens persistentes e routing key aprovados.
  - Arquivos/módulos: `document/messaging/rabbitmq`, configuração Spring e `infra/`.
  - Verificação: teste de integração publica e consome a mensagem no broker isolado e confirma propriedades de durabilidade/persistência configuradas.

- [ ] T-016 [FR-001][FR-005] Expor o ponto de entrada interno do Application Service para solicitar o armazenamento assíncrono, sem criar ou alterar endpoint REST.
  - Arquivos/módulos: `document/application` ou adaptação incremental de `DocumentService`; `DocumentController` e DTOs existentes permanecem fora da mudança.
  - Verificação: teste do Application Service confirma delegação ao publisher; revisão do diff confirma ausência de nova rota HTTP e regressão nos endpoints existentes.

- [ ] T-017 [FR-004][FR-006] Configurar acknowledgment, retry, backoff, DLX e DLQ exatamente conforme as decisões aprovadas, sem defaults implícitos do framework.
  - Arquivos/módulos: consumidor e configuração do adaptador RabbitMQ.
  - Verificação: teste de integração observa confirmação após sucesso, DLQ imediata para mensagem inválida e DLQ após o limite para falha transitória.

## Fase 4 — Testes

- [ ] T-018 [FR-002][FR-003][FR-006] Cobrir schema JSON v1, campos obrigatórios, referência de staging inválida e conversão para a chamada de storage.
  - Arquivos/módulos: testes em `backend/src/test/java/com/dockflow/dockflow/document/messaging/rabbitmq/`.
  - Verificação: mensagens válidas são traduzidas corretamente; mensagens inválidas não chamam `store` e seguem o caminho direto para DLQ.

- [ ] T-019 [FR-001][FR-005] Cobrir publisher confirm, confirmação ausente, timeout, broker indisponível e publicação rejeitada.
  - Arquivos/módulos: testes do publisher e do Application Service.
  - Verificação: somente publisher confirm gera aceitação; cada falha é propagada/sinalizada e não produz sucesso silencioso.

- [ ] T-020 [FR-003][FR-004][FR-006] Cobrir `exists(objectKey)` verdadeiro, `exists` falso, leitura do staging, `store`, limpeza, acknowledgment e falhas da porta.
  - Arquivos/módulos: testes unitários do consumidor, porta `DocumentStorage` e adaptadores.
  - Verificação: objeto existente não é gravado novamente; no caminho novo a ordem é preservada; falha do storage não remove staging antes da confirmação final.

- [ ] T-021 [FR-006] Cobrir três retries além da entrega inicial nas filas de `5s`, `15s` e `60s`, mensagem inválida sem retry, falha não recuperável e encaminhamento à DLQ.
  - Arquivos/módulos: testes do consumidor e integração com RabbitMQ isolado.
  - Verificação: contagem e destinos correspondem à política aprovada e não há reprocessamento infinito.

- [ ] T-022 [FR-003][FR-004][FR-006] Cobrir duplicidade e concorrência de consumidores na verificação de `exists(objectKey)`, incluindo o encaminhamento para as filas de prioridade definido para a disputa.
  - Arquivos/módulos: testes de integração do consumidor, configuração das filas de prioridade e storage fake/isolado; mecanismo de concorrência aprovado.
  - Verificação: o teste confirma o comportamento aprovado para duas entregas da mesma mensagem, o encaminhamento ordenado e não aceita uma garantia de atomicidade que a porta não fornece.

- [ ] T-023 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006] Executar a regressão do backend e confrontar Spec, plano, código e testes.
  - Arquivos/módulos: suíte existente em `backend/src/test/java/` e documentação da feature.
  - Verificação: testes unitários, integração e contexto da aplicação passam; qualquer divergência é registrada antes de declarar a feature pronta.

## Fase 5 — Entrega

- [ ] T-024 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006] Documentar configuração local, topologia, retry/DLQ, observabilidade e procedimento de rollback sem expor segredos.
  - Arquivos/módulos: `docs/`, `infra/.env.example` e arquivos de configuração aplicáveis.
  - Verificação: outra pessoa consegue identificar os parâmetros necessários, iniciar o ambiente aprovado e seguir o rollback sem editar dados diretamente.

- [ ] T-025 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006] Revisar o diff, riscos, decisões arquiteturais e critérios de aceite antes de qualquer deploy.
  - Arquivos/módulos: Spec, plano, tarefas, código e testes da feature.
  - Verificação: checklist de convergência preenchido, riscos residuais explicitados e aprovação de Marcos registrada; nenhum deploy é executado antes dessa aprovação.

## Adiado (fora do escopo desta rodada)

- [ ] Implementar outbox ou transação distribuída entre PostgreSQL e RabbitMQ.
- [ ] Alterar a máquina de estados do documento para representar resultados do consumo.
- [ ] Implementar a política de reconciliação de `RESULT_UNKNOWN` além da integração definida com a porta.
- [ ] Alterar a ingestão do binário ou o staging MinIO; esses componentes já existem e não fazem parte deste contrato.
- [ ] Criar endpoint REST para receber binário ou disparar/acompanhar o processamento assíncrono.
- [ ] Adicionar tracing distribuído ou métricas dedicadas.
