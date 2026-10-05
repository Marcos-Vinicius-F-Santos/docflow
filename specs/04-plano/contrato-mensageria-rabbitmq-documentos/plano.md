# Plano de Implementação — Contrato de mensageria RabbitMQ para o fluxo assíncrono de documentos

Spec relacionada: specs/03-features/contrato-mensageria-rabbitmq-documentos/spec.md  
Status: Encerrado — consolidado em `registro-binario-e-armazenamento-assincrono-documentos`

## 1. Resumo técnico

A implementação ficará restrita ao domínio `document` e usará o subpacote
`document/messaging/rabbitmq`, já previsto na arquitetura para publishers, consumidores
e contratos de cada feature. O Application Service dependerá de uma abstração de
publicação, enquanto o adaptador RabbitMQ ficará responsável pelos detalhes do broker.

O contrato será um JSON v1 chamado `DocumentStorageRequested`, com os seis campos
definidos na Spec. A mensagem carregará `contentReference`, não o binário. O consumidor
verificará `exists(objectKey)` antes de gravar, resolverá o conteúdo de staging, chamará
`DocumentStorage.store`, removerá o staging somente após o armazenamento final ser
confirmado e só então confirmará o consumo.

A publicação usará publisher confirms síncronos. Mensagens inválidas irão diretamente
para a DLQ; falhas transitórias terão até três tentativas com backoff antes da DLQ. A
implementação usará logs estruturados com `documentId` como correlação e não introduzirá
outbox, tracing distribuído ou métricas dedicadas nesta rodada.

Esta implementação também tem uma dependência de sequência: a feature de ingestão do
binário e gravação no staging precisa ser especificada, aprovada e implementada antes
deste contrato, porque a API atual não fornece conteúdo para gerar `contentReference`.

O binário já está configurado com MinIO e a etapa anterior do fluxo já produz um
`contentReference` de staging. Este plano começa no contrato/publisher/consumer; não
reimplementa a ingestão.

Ainda há gates técnicos antes do código: definir os nomes operacionais de DLX/DLQ e das
filas de retry, timeout do publisher confirm, a operação de leitura do staging — a
porta atual `DocumentStorage` só expõe `store`, `exists` e `delete` — e o tratamento de
concorrência entre consumidores por filas de prioridade. Essas lacunas não serão
decididas silenciosamente.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` ou equivalente na camada `document/application` | Adicionar o ponto de entrada interno que solicita o armazenamento assíncrono, preservando os casos existentes de registro e consulta e consumindo o staging já produzido. | Médio: o serviço atual possui fluxos existentes que não podem regredir ao incluir a publicação. |
| `backend/src/main/java/com/dockflow/dockflow/document/port/out/storage/DocumentStaging.java` e `adapter/out/storage/minio/MinioDocumentStaging.java` | Reutilizar o staging MinIO existente como origem de `contentReference`; não alterar a ingestão nesta feature. | Baixo/médio: o contrato de referência precisa permanecer compatível com o consumidor. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java` | Reutilizar `exists`, `store` e `delete`; definir, antes do código, como o consumidor lerá o staging sem acoplar-se ao MinIO. | Alto: adicionar uma operação de leitura altera a porta provider-neutral e exige compatibilidade com todos os adaptadores e testes. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorage.java` | Disponibilizar a leitura do staging somente se a decisão aprovar a extensão da porta; manter o mapeamento atual de falhas. | Alto: mudanças podem afetar a integração MinIO e a distinção entre falhas retryable e não retryable. |
| `backend/pom.xml` | Adicionar dependências de mensageria e suporte de teste para RabbitMQ. | Médio: dependências e autoconfiguração podem afetar inicialização e testes do backend. |
| `backend/src/main/resources/application.properties` e `infra/.env.example` | Adicionar configuração externa para conexão, publisher confirms, topologia, retry/DLQ e MinIO usando `MinEndpoint` e `MinSecurity`, sem credenciais hardcoded. | Médio: configuração ausente ou inválida pode impedir a inicialização; o mapeamento da credencial de `MinSecurity` precisa ser explícito. |
| `infra/docker-compose.yml` | Adicionar o serviço local RabbitMQ e sua configuração operacional aprovada. | Médio: alteração pode afetar o ambiente local e o procedimento de subida dos serviços existentes. |
| `backend/src/test/java/com/dockflow/dockflow/document/` | Adicionar testes do publisher, consumidor, contrato, idempotência e integração, mantendo a regressão atual. | Baixo/médio: testes de mensageria podem ficar acoplados a infraestrutura externa se não houver isolamento adequado. |
| `backend/src/main/resources/db/migration/` | Nenhuma migration será criada ou alterada. | Baixo: preservar o histórico Flyway evita impacto em dados e facilita rollback. |

Não será criada uma raiz genérica como `messaging/`, `rabbitmq/` ou `shared/`. A
arquitetura já reserva `document/messaging/rabbitmq` para este conteúdo; a subdivisão
será específica da feature e não haverá módulo compartilhado antecipado. Uma extensão
da porta `DocumentStorage` só será feita se for a solução aprovada para ler o staging;
nesse caso, ela será justificada pelo limite provider-neutral já existente.

## 3. Componentes novos

- Contrato JSON `DocumentStorageRequested` v1 e sua validação, dentro do limite da
  feature em `document/messaging/rabbitmq`.
- Adaptador publisher específico da feature, com publisher confirms síncronos e
  propagação de falhas ao Application Service.
- Consumidor específico da feature, com validação, verificação de idempotência por
  `exists(objectKey)`, resolução de `contentReference`, chamada de `store`, limpeza
  controlada do staging e confirmação após sucesso.
- Configuração do exchange `direct` durável, fila durável, mensagens persistentes,
  routing key `document.storage.requested`, três filas de retry com atrasos de `5s`,
  `15s` e `60s`, e DLX/DLQ conforme os identificadores operacionais aprovados.
- Ponto de entrada interno do Application Service para solicitar o processamento
  assíncrono, sem novo endpoint REST.
- Operação provider-neutral de leitura do staging, somente se a decisão da Fase 1
  confirmar que a própria porta `DocumentStorage` deve suportá-la; a alternativa de um
  resolvedor de conteúdo separado também deve ser avaliada explicitamente.
- Logs estruturados nos pontos de publicação, início do consumo, sucesso e falha,
  sempre com `documentId`.
- Testes unitários do contrato, publisher, consumidor e idempotência, além de teste de
  integração com RabbitMQ isolado para o fluxo aprovado.

Não serão criados nesta rodada uma outbox, uma transação distribuída, uma política de
estado adicional do documento, tracing distribuído ou métricas dedicadas.

## 4. Mudança de dados/banco (se houver)

Não haverá mudança de banco nesta feature. As migrations existentes permanecerão
inalteradas, pois a Spec não define persistência de outbox, tentativas, estado de
publicação ou transições adicionais do documento.

O rollback operacional será feito revertendo o código e removendo/desativando a
configuração de mensageria conforme o procedimento aprovado, sem executar alterações
destrutivas no PostgreSQL. Se a solução escolhida para consistência exigir outbox ou
qualquer novo estado persistido, a implementação deverá parar e receber uma Spec/decisão
aprovada antes de criar migration.

## 5. Sequência de implementação

### Fase 1 — Base

1. Registrar como decisões de implementação o JSON v1, o nome da mensagem, os campos,
   `contentReference`, a topologia `direct` durável, mensagens persistentes, routing key,
   publisher confirms, ausência de outbox, três retries além da entrega inicial nas filas
   de `5s`, `15s` e `60s`, DLQ, idempotência, filas de prioridade e logs estruturados.
2. Fechar os detalhes ainda bloqueadores: timeout do publisher confirm, nomes da DLX/DLQ
   e das filas de retry, formato da referência de staging, operação de leitura, remoção
   imediata quando o objeto final já existir e regra de encaminhamento para filas de
   prioridade em caso de concorrência.
3. Preparar a estrutura específica em `document/messaging/rabbitmq`, a abstração de
   publicação usada pelo Application Service e as configurações externas necessárias.
4. Garantir que não haverá migration e que as operações atuais de registro e consulta
   continuarão com suas dependências e contratos.

**Saída da fase:** contrato e decisões operacionais registradas, solução de leitura do
staging aprovada e backend preparado para compilar com configuração de RabbitMQ externa.

### Fase 2 — Lógica principal

1. Implementar o modelo JSON v1 e sua validação.
2. Implementar a publicação pelo Application Service com publisher confirm síncrono;
   falha ou ausência de confirmação não deve reportar aceitação assíncrona.
3. Implementar o consumidor: validar, resolver `contentReference`, verificar
   `exists(objectKey)` e evitar uma segunda gravação quando o objeto final já existir.
4. Quando o objeto final não existir, chamar `DocumentStorage.store`, remover o staging
   somente após sucesso e confirmar o consumo na ordem aprovada.
5. Aplicar três retries além da entrega inicial nas filas de `5s`, `15s` e `60s` para
   falhas transitórias, encaminhar mensagens inválidas diretamente para DLQ e encaminhar
   falhas não recuperáveis para DLQ sem retry.
6. Emitir logs estruturados de publicação, início, sucesso e falha com `documentId`.

**Saída da fase:** caminho lógico do publisher ao consumidor coberto, com idempotência,
limpeza segura do staging e falhas sem sucesso silencioso.

### Fase 3 — Interface

1. Integrar os adaptadores ao RabbitMQ com exchange `direct`, fila durável, mensagens
   persistentes e routing key aprovados.
2. Configurar publisher confirms, acknowledgment, filas de retry de `5s`, `15s` e `60s`,
   backoff, DLX, DLQ e filas de prioridade sem deixar valores operacionais implícitos no
   framework.
3. Expor apenas o ponto de entrada interno do Application Service; não alterar os
   endpoints REST existentes.
4. Garantir que o consumidor dependa de `DocumentStorage` ou do resolvedor provider-neutral
   aprovado, nunca do SDK do MinIO na lógica da aplicação.

**Saída da fase:** publisher e consumidor executáveis no ambiente configurado, com
RabbitMQ isolado na borda e a porta de storage preservada.

### Fase 4 — Testes

1. Testar schema JSON v1, campos obrigatórios, `contentReference` e mensagens inválidas.
2. Testar publicação bem-sucedida, publisher confirm, ausência de confirmação e falha do
   broker.
3. Testar consumo válido, `exists` verdadeiro sem novo `store`, leitura do staging,
   armazenamento final, limpeza após sucesso e ordem de acknowledgment.
4. Testar falha transitória com três retries além da entrega inicial nas filas de `5s`,
   `15s` e `60s`, mensagem inválida sem retry, falha não recuperável e encaminhamento à
   DLQ.
5. Testar duplicidade e concorrência conforme a estratégia aprovada para
   `exists(objectKey)`.
6. Executar a regressão existente do backend e verificar que registro, consulta,
   persistência e adaptador MinIO não regrediram.

### Fase 5 — Entrega (deploy/rollback)

1. Atualizar a documentação de configuração local e operação do RabbitMQ, sem incluir
   segredos.
2. Revisar diff, dependências, configurações de retry/DLQ, logs de falha e procedimento
   de rollback.
3. Confirmar a convergência `Spec da Feature ↔ Plano/Tarefas ↔ Código ↔ Testes`.
4. Aguardar a revisão e aprovação de Marcos antes de qualquer deploy.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| A porta atual `DocumentStorage` não oferece leitura do objeto de staging, embora o consumidor precise resolver `contentReference`. | Alta | Alto: o fluxo não pode ser implementado sem mudar a porta ou criar outro limite de saída. | Decidir antes do código entre estender `DocumentStorage` com uma leitura provider-neutral ou criar um resolvedor específico; cobrir o adaptador correspondente. | Sim, porque altera um limite arquitetural existente. |
| A etapa de ingestão/staging existente pode produzir referência incompatível com o consumidor. | Baixa/média | Alto: o consumidor não conseguiria ler o conteúdo ou poderia remover a referência errada. | Testar o contrato entre `DocumentStaging`, `contentReference` e resolvedor; manter a ingestão fora desta feature. | Não, salvo mudança do limite entre ingestão e mensageria. |
| Topologia, DLX/DLQ, filas de retry e filas de prioridade podem divergir entre ambientes. | Média | Alto: mensagens podem ser perdidas, retentadas indefinidamente ou roteadas incorretamente. | Centralizar configuração externa, testar broker isolado e registrar nomes/limites aprovados; não aceitar defaults implícitos. | Sim, porque a política de prioridade/retry afeta durabilidade e entrega. |
| Publisher confirm síncrono pode deixar o resultado ambíguo em timeout e causar duplicação quando a publicação ocorreu. | Média | Alto: nova publicação pode gerar entrega duplicada. | Tratar sem confirmação como falha, testar redelivery e depender da idempotência por `exists`; documentar timeout e limites. | Sim, se for necessário mudar para outbox ou outro mecanismo de garantia. |
| Dois consumidores podem verificar `exists(objectKey)` simultaneamente antes de `store`. | Média | Alto: ambos podem gravar o mesmo objeto. | Encaminhar a disputa conforme a política de filas de prioridade aprovada, testar duplicidade e preservar `objectKey` determinístico; não assumir que `exists` é uma operação atômica. | Sim, porque filas de prioridade alteram a topologia e o comportamento de concorrência. |
| Falha na limpeza do staging após o armazenamento final pode gerar objetos órfãos ou redelivery desnecessário. | Média | Médio/alto: custo e inconsistência operacional, embora o objeto final possa estar correto. | Definir a semântica de confirmação da limpeza e testar `store` bem-sucedido com `delete` falho; não apagar staging antes do `store`. | Não inicialmente; sim se exigir novo estado ou fluxo de reconciliação. |
| A política de estados para `UNAVAILABLE`, `REJECTED` e `RESULT_UNKNOWN` continua fora desta feature. | Média | Alto: um consumidor futuro pode marcar o documento incorretamente. | Não alterar estados nesta rodada; manter `REJECTED` sem retry, `UNAVAILABLE` na política de retry e `RESULT_UNKNOWN` na feature de reconciliação. | Não por padrão; sim se o consumidor passar a mudar limites de domínio. |

## 7. Perguntas abertas antes de começar

- Qual será o timeout do publisher confirm?
- Quais serão os nomes operacionais da DLX, da DLQ e das três filas de retry?
- Qual é o formato exato de `contentReference` e como a leitura do staging será exposta:
  extensão de `DocumentStorage` ou resolvedor provider-neutral separado?
- Como as instâncias que verificarem o mesmo `objectKey` serão detectadas e encaminhadas
  às filas de prioridade? Qual será a prioridade e a regra de ordenação?
- A limpeza do staging precisa ser confirmada antes do acknowledgment ou uma falha de
  `delete` será tratada separadamente após o storage final?
- A variável `MinSecurity` conterá quais credenciais/valores e como será mapeada para o
  cliente MinIO, mantendo compatibilidade com a configuração local existente?
