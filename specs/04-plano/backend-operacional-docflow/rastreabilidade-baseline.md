# Baseline de rastreabilidade — Backend operacional do DocFlow

Tarefa: T-001  
Feature: `specs/03-features/backend-operacional-docflow/spec.md`  
Data do levantamento: 2026-09-18  
Status da tarefa: em revisão — não marcada como concluída

## 1. Escopo do levantamento

Esta matriz cruza os FRs e ACs da Spec aprovada com o estado observável no código,
testes, infraestrutura e documentação. Ela é um baseline para orientar as próximas
tarefas; não considera arquivo apenas presente no worktree como implementação concluída.

O caminho oficial confirmado para a feature é:

```text
specs/03-features/backend-operacional-docflow/
specs/04-plano/backend-operacional-docflow/
```

O nome `backend-operacional-dockflow` não existe no repositório.

### Legenda

- **Existente** — há código/teste/documentação diretamente correspondente, sem lacuna
  relevante observada nesta tarefa.
- **Parcial** — há parte do contrato implementada, mas faltam elementos descritos na
  Spec ou evidência real suficiente.
- **Ausente** — não foi encontrado código/teste/documentação correspondente.
- **Divergente** — existe comportamento ou documentação, mas não corresponde ao contrato
  aprovado.
- **Não verificado** — a evidência exige execução de teste/ambiente e não foi declarada
  como aprovada nesta tarefa.

## 2. Estado do worktree

O worktree já continha alterações modificadas, removidas e não versionadas antes desta
tarefa, incluindo backend, infraestrutura, documentação e specs. Em particular, foram
encontrados `V4__add_reconciliation_attempts.sql`, adaptadores MinIO, publisher/consumer
RabbitMQ, testes de integração e a Spec da feature.

Esses arquivos foram usados como evidência do estado atual, mas não foram marcados como
concluídos nem tiveram comportamento alterado por T-001. A matriz deve ser revisitada
após cada tarefa de implementação.

## 3. Matriz de requisitos funcionais

| Requisito | Estado observado | Evidência existente | Lacuna/observação |
|---|---|---|---|
| FR-001 — isolamento dos testes | **Parcial / divergente** | Testes unitários usam mocks; `IntegrationTestBase.java` inicia PostgreSQL; `DocumentRegistrationEndToEndTest.java` inicia PostgreSQL, RabbitMQ e MinIO próprios. | `@SpringBootTest` usa configuração RabbitMQ por default e a Spec registra sete erros ao tentar `localhost:5672`; não há isolamento completo para todos os contextos. |
| FR-002 — ambiente local reproduzível | **Parcial** | `infra/docker-compose.yml`, `infra/.env.example` e `docs/document-registration-operations.md` descrevem PostgreSQL, RabbitMQ, MinIO e buckets. | Não há serviço de bootstrap; senhas podem estar vazias; buckets exigem criação manual; volumes podem manter credenciais antigas. |
| FR-003 — reconciliação operacional | **Parcial** | `DocumentReconciliationService.java` e `ReconcileUnknownStorageResultUseCase.java` consultam `DocumentStorage.exists`. | Não há scheduler, comando fallback nem consulta de documentos elegíveis com claim atômico de reconciliação. |
| FR-004 — resultado da reconciliação | **Parcial** | `DocumentReconciliationServiceTest.java` cobre manter `PROCESSING`, completar quando existe e falhar após limite injetado; V4 persiste `reconciliation_attempts`. | O limite atual não é o contrato de cinco tentativas, os atrasos não existem e a próxima data elegível não é persistida. |
| FR-005 — falhas permanentes | **Parcial / divergente** | `Document.java` possui `markFailed()` e as transições de domínio; o consumidor classifica falha não retryable como `DEAD_LETTER`. | `DocumentStorageMessageConsumer` retorna DLQ sem marcar necessariamente o documento como `FAILED`; retries esgotados e falhas definitivas podem deixar `PROCESSING`. |
| FR-006 — timeout real | **Divergente** | `DocumentStorageRabbitTopology.PROCESSING_TIMEOUT` existe com 3 segundos. | O timeout é usado nas confirmações RabbitMQ em publisher/retry; `MinioDocumentConfiguration` não configura timeouts de conexão, leitura e escrita, e não há teste de timeout do processamento. |
| FR-007 — retries e DLQ | **Parcial** | `DocumentStorageRabbitTopology`, `DocumentStorageRabbitConfiguration` e `RabbitMqDocumentStorageMessageHandler` definem filas `5s/15s/60s`, DLX/DLQ, `ack`, `nack` e requeue. | O comportamento existe no código, mas falta validá-lo com RabbitMQ real para todos os caminhos e alinhar estado `FAILED`/`PROCESSING`. |
| FR-008 — validação real da mensageria | **Parcial** | `DocumentRegistrationEndToEndTest` usa `RabbitMQContainer` no caminho feliz. | Não há evidência de testes reais específicos para TTLs, retries, DLQ, publisher confirm, mensagem inválida e falha de publicação do retry. |
| FR-009 — concorrência explícita/idempotente | **Parcial** | `DocumentRepository.claimForProcessing` possui update condicional e lease de 10 segundos; `DocumentServiceIntegrationTest` cobre claim/takeover. | `DocumentStorageRabbitConfiguration` não declara consumer/prefetch/concurrency explicitamente; claim de reconciliação ainda não existe. |
| FR-010 — teste real de concorrência | **Ausente / parcial** | Há teste de claim PostgreSQL, mas não teste com mensagens duplicadas e consumidores RabbitMQ concorrentes. | Falta comprovar uma única gravação no storage final e nenhum cleanup prematuro pelo consumidor perdedor. |
| FR-011 — alinhamento documental | **Divergente** | Existem `README.md`, `docs/api.md`, `docs/database.md`, `docs/document-registration-operations.md`, arquitetura e specs relacionadas. | README, `docs/architecture.md`, Spec do Produto e Arquitetura ainda tratam partes já presentes como futuras ou fora da feature; operação local ainda exige passos manuais. |

## 4. Matriz de critérios de aceite

| Critério | Estado observado | Evidência existente | Lacuna/observação |
|---|---|---|---|
| AC-001 — ambiente e fluxo assíncrono feliz | **Parcial / não verificado em ambiente limpo** | `DocumentRegistrationEndToEndTest` publica, consome, grava no MinIO final, consulta o documento e verifica remoção do staging. | O teste cria buckets manualmente; não valida checkout limpo + `.env.example` + bootstrap separado. |
| AC-002 — reconciliação confirma objeto | **Parcial** | `DocumentReconciliationServiceTest.shouldCompleteWhenStorageConfirmsObject`. | O caso de uso funciona isoladamente, mas não há disparo pelo scheduler nem claim operacional. |
| AC-003 — falha permanente sem documento preso | **Ausente / divergente** | `DocumentStorageMessageConsumer` possui disposição `DEAD_LETTER`; `Document.markFailed()` existe. | Não há evidência de `REJECTED → FAILED` antes do encerramento da mensagem nem de falha transitória definitiva sem `PROCESSING` preso. |
| AC-004 — quinta reconciliação sem confirmação | **Parcial** | Teste unitário falha após limite configurável; contador V4 existe. | Não há cinco tentativas com atrasos `1m/5m/15m/30m/60m`, elegibilidade persistida ou scheduler. |
| AC-005 — timeout real | **Ausente** | Existe constante de 3 segundos para confirmação RabbitMQ. | Não há timeout MinIO real, mapeamento comprovado para `RESULT_UNKNOWN`, ausência de `ACK` e preservação de `PROCESSING`. |
| AC-006 — retries, DLQ e ack/nack reais | **Parcial** | Topologia e handler RabbitMQ existem; há testes unitários do handler/publisher. | Falta teste contra broker real observando filas, TTL, DLQ, requeue e ordem de confirmação. |
| AC-007 — concorrência real | **Parcial** | Teste PostgreSQL confirma exclusividade da lease de processamento. | Falta teste end-to-end com mensagens duplicadas, RabbitMQ, storage real e configuração `1/1/1`. |
| AC-008 — isolamento dos testes | **Divergente** | Unitários usam mocks e integrações possuem alguns containers próprios. | Testes de contexto ainda podem iniciar o listener e tentar RabbitMQ externo; os sete erros atuais permanecem como baseline. |
| AC-009 — documentação coerente | **Divergente** | Documentos oficiais existem e descrevem partes do fluxo. | Fontes divergem sobre RabbitMQ, scheduler, bootstrap, buckets manuais, reconciliação e prontidão operacional. |

## 5. Arquitetura e limites confirmados

O código existente está organizado dentro do domínio `document`, com as seguintes
fronteiras já disponíveis:

- domínio: `Document.java` e `DocumentStatus.java`;
- aplicação: `document/application/`;
- entrada HTTP: `document/controller/` e `document/dto/`;
- persistência: `DocumentRepository.java` e migrations Flyway;
- portas de storage: `document/port/out/storage/`;
- adaptadores MinIO: `document/adapter/out/storage/minio/` e
  `document/storage/minio/`;
- mensageria: `document/messaging/rabbitmq/`;
- infraestrutura local: `infra/`;
- documentação: `docs/`, `README.md` e `specs/`.

T-001 não identifica necessidade de nova raiz global, módulo compartilhado ou mudança
dos limites arquiteturais. O scheduler, caso criado em tarefa posterior, deve viver no
domínio `document`, preferencialmente em `document/application/`, e usar o repositório e
as portas existentes. O bootstrap é infraestrutura local em `infra/`, não lógica de
negócio do domínio.

## 6. Documentos e fontes oficiais

| Papel | Fonte |
|---|---|
| Comportamento funcional | `specs/03-features/backend-operacional-docflow/spec.md` |
| Arquitetura e limites | `specs/02-arquitetura/ARQUITETURA.md` e ADRs |
| Contrato HTTP | `docs/api.md` |
| Operação local | `docs/document-registration-operations.md` |
| Banco e migrations | `docs/database.md` |
| Execução geral | `README.md` |
| Deploy e rollback | `specs/06-deploy/` |
| Evidência de validação | `specs/05-verificacao/` — não é fonte primária de comportamento |

No baseline, essas fontes ainda não estão alinhadas entre si. O alinhamento é FR-011 e
fica para as tarefas posteriores; T-001 apenas registra a divergência.

## 7. Resultado da tarefa T-001

- Caminho oficial da feature confirmado como `backend-operacional-docflow`.
- FR-001 a FR-011 mapeados para evidência existente e lacunas.
- AC-001 a AC-009 mapeados para evidência existente e lacunas.
- Alterações do worktree separadas de comportamento comprovado.
- Organização arquitetural existente registrada; nenhuma estrutura nova foi proposta.
- Nenhuma tarefa foi marcada como concluída.

## 8. Próxima tarefa autorizada pelo plano

Após aprovação desta matriz, a próxima tarefa é T-002: definir o modelo persistido da
reconciliação — próxima data elegível, contador, claim e tratamento de documentos já
`PROCESSING`. T-001 não implementa migration, scheduler, configuração, bootstrap ou
testes novos.
