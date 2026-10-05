# Plano de Implementação — Completar e estabilizar o backend operacional do DocFlow

Spec relacionada: `specs/03-features/backend-operacional-docflow/spec.md`  
Status: Rascunho

## 1. Resumo técnico

A implementação será concluída dentro do domínio `document`, reutilizando as classes,
portas e adaptadores já previstos em `specs/02-arquitetura/ARQUITETURA.md`. O trabalho
vai corrigir o wiring do contexto de testes, tornar PostgreSQL/RabbitMQ/MinIO
reproduzíveis localmente, completar o scheduler de reconciliação e ajustar o consumidor
RabbitMQ para falhas permanentes, timeout real, retry, DLQ e concorrência explícita.

O scheduler será um componente interno da aplicação, com claim atômico no repositório de
documentos e cinco tentativas nos atrasos aprovados. O cliente MinIO receberá timeouts
de conexão, leitura e escrita de três segundos; timeout será traduzido para
`RESULT_UNKNOWN`, sem `ACK` e sem interrupção forçada de thread.

As alterações de banco serão feitas por migration Flyway expand-only. Não será criado um
novo módulo de negócio: o domínio, o application service, o repositório, os adaptadores
RabbitMQ/MinIO, a infraestrutura local e a documentação existentes continuam sendo os
limites da feature.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/src/main/java/.../document/Document.java` | Modelar estado da reconciliação, próxima elegibilidade e regras de contagem/claim sem expor mutações inválidas. | Alto: alterações de estado podem quebrar transições existentes ou permitir dupla reconciliação. |
| `backend/src/main/java/.../document/DocumentRepository.java` | Adicionar consultas/updates atômicos para selecionar e reivindicar documentos elegíveis, respeitando a lease atual de processamento. | Alto: SQL incorreto pode duplicar processamento ou assumir documentos ainda sob lease. |
| `backend/src/main/java/.../document/application/DocumentReconciliationService.java` e `ReconcileUnknownStorageResultUseCase.java` | Trocar o caso de uso acionado apenas internamente por fluxo acionável pelo scheduler, com backoff de cinco tentativas e conclusão em `COMPLETED`/`FAILED`. | Alto: o resultado do storage não pode ser promovido silenciosamente a sucesso. |
| `backend/src/main/java/.../document/messaging/rabbitmq/` | Tornar explícitos consumidor único, `prefetch=1`, concorrência `1..1`, timeout/resultado desconhecido e transições para falhas permanentes. | Alto: `ack`, `nack`, retry e DLQ podem causar perda, duplicidade ou documento preso. |
| `backend/src/main/java/.../document/adapter/out/storage/minio/` e `document/storage/minio/` | Configurar timeouts do cliente MinIO e preservar o mapeamento de falhas para `RESULT_UNKNOWN`, `UNAVAILABLE` e `REJECTED`. | Alto: timeouts separados podem alterar o comportamento real de staging e storage final. |
| `backend/src/main/resources/application.properties` | Declarar propriedades de timeout, scheduler, RabbitMQ, MinIO e buckets sem valores vazios silenciosos. | Médio: configuração incompleta pode impedir o boot ou conectar no serviço errado. |
| `backend/src/main/resources/db/migration/V4__add_reconciliation_attempts.sql` e nova migration | Preservar V4 e adicionar somente campos necessários para próxima tentativa/claim de reconciliação, com compatibilidade para dados existentes. | Alto: migration incorreta pode impedir o boot ou perder documentos `PROCESSING`. |
| `backend/src/test/java/.../IntegrationTestBase.java` e testes `@SpringBootTest` | Separar testes unitários, testes de contexto e testes reais com Testcontainers; remover dependência implícita de `localhost:5672`. | Alto: pode aumentar o tempo da suíte e ainda deixar dependências externas ocultas. |
| `DocumentRegistrationEndToEndTest` e novos testes de integração | Consolidar fixtures reais de PostgreSQL, RabbitMQ e MinIO, incluindo topologia, retry, DLQ, timeout e concorrência. | Alto: testes assíncronos podem ser flakey se dependerem de sleeps não determinísticos. |
| `infra/docker-compose.yml` e `infra/.env.example` | Adicionar serviço separado de bootstrap, validar credenciais e criar buckets idempotentemente. | Médio/alto: volumes persistentes podem manter credenciais antigas e mascarar configurações. |
| `docs/api.md`, `docs/document-registration-operations.md`, `docs/database.md`, `README.md`, `specs/02-arquitetura/` e specs relacionadas | Alinhar comportamento implementado, operação local, migrations, topologia, reconciliação, fontes oficiais e limites. | Médio: documentação divergente pode fazer a operação manual voltar a ser necessária. |
| `specs/05-verificacao/` e `specs/06-deploy/` | Registrar evidências de validação e procedimentos de entrega/rollback sem transformar esses documentos em fonte primária do comportamento. | Médio: checklist pode declarar sucesso sem evidência ponta a ponta. |

O worktree atual contém alterações e arquivos não versionados anteriores a este plano.
Esses arquivos serão revisados como evidência do estado atual, mas não serão tratados
como concluídos sem passar pelas tarefas, testes e revisão de convergência desta feature.

## 3. Componentes novos

- Serviço separado de bootstrap no Compose, responsável por aguardar PostgreSQL,
  RabbitMQ e MinIO, validar conexões e criar `docflow-staging` e
  `docflow-documents` de forma idempotente.
- Componente de scheduler interno no domínio `document`, preferencialmente em
  `document/application/` ou em uma subdivisão do domínio somente se já houver conteúdo
  suficiente para justificá-la.
- Operações provider-neutral no repositório/application service para selecionar,
  reivindicar e reagendar reconciliações; não criar uma nova camada global de
  `scheduler`, `repositories` ou `workers` fora do domínio.
- Fixtures reutilizáveis de integração para PostgreSQL, RabbitMQ e MinIO, sem substituir
  os testes unitários por dependências externas.
- Se necessário, uma migration expand-only posterior à V4 para persistir a próxima
  elegibilidade e o estado mínimo de claim da reconciliação. A necessidade exata deve
  ser confirmada na tarefa de base antes de criar o arquivo.

## 4. Mudança de dados/banco (se houver)

A migration V4 existente, que adiciona `reconciliation_attempts`, deve ser preservada.
O scheduler precisa persistir pelo menos a próxima data elegível da reconciliação para
retomar o fluxo após reinício da aplicação. O claim deve ser atômico e não pode depender
da memória do processo.

Na Fase 1, será decidido entre:

1. reutilizar colunas existentes e o controle de versão para fazer o claim e persistir a
   próxima tentativa; ou
2. criar uma migration expand-only, por exemplo V5, com as colunas mínimas necessárias
   para próxima tentativa e claim/lease de reconciliação.

A alternativa escolhida deve:

- manter dados existentes válidos;
- dar valor seguro a documentos já `PROCESSING`;
- permitir que apenas um scheduler reivindique cada documento;
- não modificar V1–V4 diretamente;
- ser coberta por teste de migration e integração PostgreSQL;
- possuir rollback operacional compatível com o fato de que migrations Flyway não são
  revertidas automaticamente pelo Flyway.

Se a alternativa exigir uma mudança de limite arquitetural ou uma estratégia nova de
locking, deve ser criado um Registro de Decisão antes da implementação dessa parte.

## 5. Sequência de implementação

### Fase 1 — Base

1. Mapear os FRs e ACs da Spec aprovada para o código, testes, infraestrutura e
   documentação existentes, separando mudanças locais não revisadas de comportamento
   comprovado.
2. Definir o contrato de persistência da reconciliação: elegibilidade, cinco tentativas,
   backoff, claim atômico, retomada após reinício e migração dos documentos existentes.
3. Isolar os contextos de teste: unitários sem infraestrutura externa, integração com
   Testcontainers e end-to-end com a stack completa controlada pela própria suíte.
4. Declarar a configuração operacional necessária: timeout MinIO de 3 segundos,
   scheduler, RabbitMQ com um consumidor/prefetch 1/concorrência 1..1 e credenciais sem
   fallback vazio silencioso.
5. Confirmar que a solução continua dentro de `document/`, `infra/`, `docs/` e `specs/`,
   sem criar estrutura global nova.

### Fase 2 — Lógica principal

1. Implementar o modelo e o repositório de reconciliação, incluindo claim atômico,
   próxima tentativa elegível e transições protegidas para `COMPLETED` e `FAILED`.
2. Implementar o scheduler interno periódico, com cinco tentativas nos atrasos de
   `1m`, `5m`, `15m`, `30m` e `60m`, liberando o claim em sucesso e erro.
3. Ajustar o consumidor para marcar `FAILED` em `REJECTED` e falhas permanentes
   identificáveis, mantendo `RESULT_UNKNOWN` em `PROCESSING` para retry/reconciliação.
4. Configurar o cliente MinIO com timeout de conexão, leitura e escrita de 3 segundos e
   mapear timeout para `RESULT_UNKNOWN`, sem interrupção forçada de thread.
5. Declarar explicitamente a política RabbitMQ: um consumidor, `prefetch=1`, concorrência
   mínima 1 e máxima 1, além da política atual de retries, DLQ e requeue.
6. Criar o serviço de bootstrap local separado no Compose, com espera por dependências,
   validação explícita de credenciais/conexões e criação idempotente dos dois buckets.

### Fase 3 — Interface

1. Integrar o scheduler, as propriedades e o bootstrap ao contexto da aplicação sem
   criar endpoint REST novo como mecanismo principal.
2. Atualizar `infra/.env.example`, `application.properties` e o Compose para que os
   nomes de credenciais da aplicação sejam distintos das credenciais root do MinIO e
   para que falhas de configuração sejam explícitas.
3. Atualizar as interfaces provider-neutral e os adaptadores somente nos pontos
   necessários; controller não deve acessar banco, RabbitMQ ou MinIO diretamente.
4. Atualizar a documentação oficial: API, operação local, banco/migrations, README,
   arquitetura/ADRs e deploy/rollback. Manter `specs/05-verificacao/` como evidência.

### Fase 4 — Testes

1. Cobrir unitariamente as transições de falha, o mapeamento de timeout para
   `RESULT_UNKNOWN`, a contagem e o backoff da reconciliação.
2. Cobrir com PostgreSQL real o claim atômico, a próxima data elegível, a lease de
   processamento e a migração dos documentos existentes.
3. Cobrir com RabbitMQ real a topologia, publisher confirm, `ack`/`nack`, requeue,
   retries de `5s/15s/60s`, DLQ direta e mensagem inválida.
4. Cobrir com MinIO real o bootstrap dos buckets, gravação/leitura, credenciais e
   comportamento de timeout; manter mocks para testes unitários rápidos.
5. Cobrir concorrência real e confirmar que apenas um claim grava o storage final.
6. Executar regressão do backend, incluindo API, persistência, consulta por documento,
   testes unitários, integração e end-to-end.

### Fase 5 — Entrega (deploy/rollback)

1. Atualizar checklist de convergência e confirmar `Spec ↔ Plano/Tarefas ↔ Código ↔
   Teste` para todos os FRs e ACs.
2. Executar o procedimento local completo a partir de checkout limpo, incluindo
   `.env.example`, bootstrap, registro de documento, consumo e reconciliação.
3. Registrar o procedimento de deploy/rollback, preservando banco, objetos de staging e
   mensagens RabbitMQ; não apagar volumes automaticamente.
4. Revisar diffs, migrations, riscos e documentos oficiais; não considerar o ambiente
   pronto apenas porque a aplicação responde health check.
5. Aguardar a revisão e aprovação de Marcos antes de qualquer deploy.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| O worktree contém alterações locais e arquivos não versionados que podem ser confundidos com implementação concluída. | Alta | Alto | Tratar o estado atual como baseline; separar diff da feature, testar cada FR e não reverter alterações existentes. | Não; é controle de escopo e revisão. |
| A migration V4 existe, mas não persiste a próxima data elegível nem o claim do scheduler. | Alta | Alto | Confirmar o modelo na Fase 1; usar migration expand-only se necessário, com teste de dados existentes e rollback operacional. | Sim, se for necessário um mecanismo novo de locking/claim ou mudança de limite arquitetural. |
| Dois schedulers podem reconciliar o mesmo documento. | Média | Alto | Claim atômico no PostgreSQL, teste concorrente e liberação do claim em todos os caminhos. | Sim, se a solução exigir uma estratégia de coordenação distribuída além do repositório existente. |
| `RESULT_UNKNOWN` pode ser enviado à DLQ após retries RabbitMQ enquanto o documento continua `PROCESSING`. | Alta | Alto | Separar claramente retry RabbitMQ de reconciliação; validar scheduler, backoff e transição final para `FAILED`. | Não; o comportamento está definido na Spec aprovada. |
| Timeouts individuais do MinIO podem não limitar o tempo total percebido pelo consumidor. | Média | Alto | Configurar conexão, leitura e escrita com 3 segundos, testar operação bloqueada e verificar ausência de `ACK` prematuro. | Não, salvo se for necessário um mecanismo de deadline fora do cliente MinIO. |
| Testes reais de TTL e backoff podem ficar lentos ou flakey. | Média | Médio/alto | Usar espera por eventos/estado observável, reduzir apenas o tempo em fixtures de teste sem alterar o contrato, e reservar smoke test com tempos reais. | Não. |
| Credenciais antigas em volumes persistentes podem fazer o Compose parecer quebrado mesmo com `.env` correto. | Média | Médio | Bootstrap falha explicitamente, documentação explica o diagnóstico e rollback não remove volumes automaticamente. | Não; é procedimento operacional. |
| Atualizar produto, arquitetura, ADRs e docs pode deixar fontes conflitantes. | Média | Alto | Definir a hierarquia oficial na documentação e executar revisão de convergência antes da entrega. | Não; a hierarquia está definida pela Spec. |

## 7. Perguntas abertas antes de começar

Não há perguntas de produto ou comportamento pendentes: a Spec foi aprovada e as decisões
de scheduler, tentativas/backoff, concorrência, timeout, bootstrap e fontes oficiais
estão incorporadas neste plano.

Antes da primeira tarefa de lógica, permanecem apenas gates de execução:

- confirmar que o caminho correto da feature é
  `specs/03-features/backend-operacional-docflow/` — o nome informado como
  `backend-operacional-dockflow` não existe;
- registrar no plano/tarefas se a migration V4 já está no commit de baseline ou se é uma
  alteração local a ser revisada;
- obter aprovação deste plano e das tarefas antes de iniciar implementação.
