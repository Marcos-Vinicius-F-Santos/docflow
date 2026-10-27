# Plano de Implementação — Hub de documentos

Spec relacionada: specs/03-features/hub/spec.md  
Status: Aprovado

## 1. Resumo técnico

O Hub será uma nova tela Angular em frontend/src/app/hub/, configurada como destino da
rota /. Ele reutilizará os serviços e contratos de documentos existentes para upload,
consulta, download e exclusão, mantendo as rotas atuais acessíveis.

O backend receberá o comando POST /documents/{documentId}/retry, que reprocessará o
staging existente após lock e validação do estado. O domínio persistirá falhas,
isExcluded e exclusionScheduledAt por migration Flyway. Ao atingir três falhas,
publicará uma mensagem persistente em uma fila de exclusão com TTL de três horas; o
consumidor removerá binário, staging e metadados de forma idempotente.

A implementação deve preservar os limites atuais: regras no domínio/aplicação,
PostgreSQL para estado, RabbitMQ nas bordas assíncronas, MinIO atrás das portas de
storage e frontend sem acesso direto à infraestrutura.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| frontend/src/app/app.routes.ts | Fazer / carregar o Hub em vez de redirecionar para /documents/new; manter as três rotas de documentos. | Médio: pode quebrar navegação inicial ou links existentes. |
| frontend/src/app/document/document-api.service.ts | Adicionar chamada de retry e preservar upload, listagem, download e exclusão individuais. | Alto: erro de rota/status pode afetar o fluxo existente. |
| frontend/src/app/document/document.models.ts | Expor os novos campos retornados pelo backend e os estados necessários ao Hub. | Médio: incompatibilidade de contrato TypeScript/JSON. |
| frontend/src/app/document/pages/* | Reutilizar upload e ações existentes sem alterar seus contratos; ajustar somente integrações necessárias com o Hub. | Médio: regressão visual ou de navegação. |
| backend/.../document/Document.java | Adicionar contador, marca de exclusão, prazo e transições protegidas. | Alto: invariantes e estados persistidos podem ser corrompidos. |
| backend/.../document/DocumentService.java | Orquestrar retry, lock, publicação e exclusão manual mantendo as regras transacionais. | Alto: corrida entre retry, consumidor, exclusão manual e fila de exclusão. |
| backend/.../document/DocumentRepository.java | Adicionar consulta/lock para retry elegível e suporte à exclusão agendada. | Alto: concorrência e duplicidade de mensagens. |
| backend/.../document/controller/DocumentController.java | Expor POST /documents/{documentId}/retry e mapear 202, 404, 409 e 503. | Médio: contrato HTTP novo e tratamento de erros. |
| backend/.../document/messaging/rabbitmq/ | Atualizar consumidor para persistir falhas e criar topologia/consumidor da fila de exclusão. | Alto: ack/nack, TTL, DLQ e idempotência. |
| backend/src/main/resources/db/migration/ | Criar migration expand-only para os atributos de retry/exclusão. | Alto: dados existentes e rollback operacional. |
| backend/src/test/java/.../document/ | Adicionar testes de domínio, controller, integração PostgreSQL, RabbitMQ e MinIO. | Médio: suíte pode ficar dependente de serviços externos se o isolamento for perdido. |

Nenhuma reorganização geral de pacotes será feita. Componentes novos seguirão o domínio
document, usando document/application/ para casos de uso e
document/messaging/rabbitmq/ para contratos e adaptadores de mensageria, conforme a
arquitetura existente.

## 3. Componentes novos

- frontend/src/app/hub/ com página, template, estilos, estado de carregamento e
  componentes internos somente se houver conteúdo suficiente para justificá-los.
- Caso de uso de retry no domínio document, preferencialmente em
  document/application/, com lock e publicação da referência de staging existente.
- Campos e métodos protegidos em Document para falhas, isExcluded e
  exclusionScheduledAt.
- DTO/contrato HTTP para o resultado de retry, sem expor credenciais, bucket ou
  objectKey.
- Mensagem, exchange/fila, publisher e consumidor da exclusão atrasada em
  document/messaging/rabbitmq/.
- Migration Flyway V6 ou a próxima versão disponível, sem editar V1–V5.
- ADR novo em specs/02-arquitetura/DECISAO/ para documentar retry manual via staging,
  fila de exclusão, TTL de três horas, idempotência e corrida com exclusão manual.

## 4. Mudança de dados/banco

Será criada uma migration Flyway expand-only para adicionar ao documento:

- failure_count BIGINT NOT NULL DEFAULT 0, correspondente ao atributo de domínio
  falhas;
- is_excluded BOOLEAN NOT NULL DEFAULT FALSE, correspondente a isExcluded;
- exclusion_scheduled_at TIMESTAMPTZ NULL, correspondente a exclusionScheduledAt.

O domínio pode expor os nomes lógicos definidos na Spec; o mapeamento físico acima foi
aprovado e segue a convenção SQL existente. A migration deve definir valores seguros
para documentos existentes e não pode alterar V1–V5.

Devem ser aplicadas as restrições failure_count >= 0, is_excluded = false OR
failure_count >= 3 e exclusion_scheduled_at IS NULL OR is_excluded = true. O índice
parcial de exclusão deve usar exclusion_scheduled_at para documentos is_excluded.

Rollback operacional: não remover as colunas nem apagar dados diretamente. Reverter o
código para uma versão compatível, mantendo as colunas adicionais ignoráveis pela
versão anterior. Qualquer remoção futura exige migration própria após confirmar que
nenhuma versão ainda depende dos campos.

O índice parcial de exclusão agendada faz parte da migration e deve cobrir documentos
is_excluded = true elegíveis por exclusion_scheduled_at.

## 5. Sequência de implementação

### Fase 1 — Base

1. Fechar a matriz FR/BR/AC → contrato → código → teste da Spec e registrar o baseline
   das rotas /, /documents/new, /documents, /documents/:documentId, upload, download
   e exclusão individual.
2. Criar o ADR do retry e da fila de exclusão, incluindo lock, estados elegíveis,
   contador, isExcluded, exclusionScheduledAt, TTL, exclusão completa e mensagens
   tardias após exclusão manual.
3. Fechar o contrato HTTP de POST /documents/{documentId}/retry e os códigos de
   resposta definidos na Spec.
4. Fechar o contrato da mensagem e da topologia RabbitMQ aprovada: exchange
   docflow.document-deletion, fila de espera docflow.document-deletion.delay.3h, fila
   consumida docflow.document-deletion.requested, routing keys
   document.deletion.scheduled e document.deletion.requested, DLX
   docflow.document-deletion.dlx e DLQ docflow.document-deletion.dlq.
5. Preparar migration, fixtures e testes de contrato sem alterar o comportamento dos
   fluxos existentes.

Gate da Fase 1: ADR, contrato HTTP, contrato de mensageria, migration e perguntas de
regra revisados; baseline atual passa antes da lógica nova.

### Fase 2 — Lógica principal

1. Implementar os campos e invariantes de Document, incluindo inicialização, incremento
   de falhas, reset no sucesso, marcação de isExcluded e agenda de exclusão.
2. Implementar a migration e o mapeamento JPA dos três campos, validando documentos
   existentes e valores padrão.
3. Implementar no repositório o lock pessimista e as consultas para retry elegível,
   exclusão agendada e documento já removido.
4. Implementar o caso de uso de retry: validar FAILED, falhas < 3,
   isExcluded = false e staging existente; marcar PROCESSING; publicar a mesma
   referência de staging; preservar FAILED se a publicação não for confirmada.
5. Expor POST /documents/{documentId}/retry no controller e mapear 202, 404, 409 e
   503 para ProblemDetail sem expor detalhes de storage.
6. Atualizar o consumidor do armazenamento para incrementar falhas em falhas efetivas,
   marcar FAILED e, na terceira falha, persistir isExcluded/exclusionScheduledAt e
   publicar a mensagem de exclusão.
7. Implementar a topologia, publisher e consumidor da fila de exclusão com mensagem
   persistente contendo schemaVersion e documentId, TTL de três horas, ack/nack seguro
   e remoção idempotente de binário, staging e metadados.
8. Garantir que a exclusão manual e mensagens tardias da fila não recriem documentos,
   não reprocessam staging e não sejam reportadas como falha quando o documento já foi
   removido.
9. Adicionar ao cliente Angular o modelo e o método HTTP de retry, mantendo os
   contratos individuais existentes.

Gate da Fase 2: retry manual, limite de três falhas, marcação, fila de exclusão e
exclusão manual funcionam em testes de unidade e integração; nenhum fluxo existente
fica alterado sem cobertura.

### Fase 3 — Interface

1. Criar a página do Hub em frontend/src/app/hub/ e fazer a rota / carregá-la.
2. Implementar header com DocFlow, faixa lateral e área central conforme o wireframe,
   sem fixar dimensões ou estilos não aprovados.
3. Implementar o dropbox reutilizando o validador e o upload multipart existentes.
   Após sucesso, permanecer no Hub e atualizar a lista com o documento recebido.
4. Carregar GET /documents e exibir todos os documentos, com estado vazio,
   carregamento, erro e nova tentativa de consulta.
5. Exibir na lateral os cinco documentos ordenados por updatedAt decrescente, usando
   createdAt como fallback, e todos os documentos FAILED.
6. Exibir os indicadores dos estados: check verde para COMPLETED, refresh azul para
   PENDING/PROCESSING e X vermelho para FAILED.
7. Integrar download individual para COMPLETED, retry explícito quando elegível e
   exclusão manual conforme os contratos existentes, sem criar uma seção própria de
   downloads.
8. Garantir acessibilidade básica, foco, teclado, responsividade e coexistência das
   rotas /documents/new, /documents e /documents/:documentId.

Gate da Fase 3: o Hub é a tela inicial, o caminho principal de upload funciona, os
estados e ações estão claros e as rotas existentes continuam navegáveis.

### Fase 4 — Testes

1. Cobrir unitariamente as invariantes de Document, incluindo contagem inicial,
   incremento, reset, limite e marcação para exclusão.
2. Cobrir migration e persistência em PostgreSQL, incluindo defaults para documentos
   existentes, lock e consultas de elegibilidade.
3. Cobrir controller/serviço de retry para 202, 404, 409, 503, staging ausente,
   publicação não confirmada e requisições concorrentes.
4. Cobrir RabbitMQ real para retry, persistência da mensagem, fila de exclusão, TTL,
   ack/nack, publicação não confirmada e idempotência após exclusão manual. O teste
   pode usar TTL reduzido somente em configuração de teste; o contrato de produção
   permanece em três horas.
5. Cobrir o consumidor com falha inicial, falhas subsequentes, terceira falha,
   isExcluded, exclusionScheduledAt, remoção completa e mensagem tardia.
6. Cobrir o serviço Angular com HttpTestingController para listagem, upload, retry,
   download, exclusão e erros.
7. Cobrir o componente Hub com caminho feliz, lista vazia, falha de API, quatro estados,
   ordenação lateral, permanência após upload, retry elegível/inelegível e regressão
   das rotas existentes.
8. Executar build e suíte completa do backend e frontend, seguida de smoke test local
   com PostgreSQL, RabbitMQ, MinIO, backend e frontend.

Gate da Fase 4: AC-001 a AC-009 passam com evidência; migration, retry, exclusão
atrasada e regressão dos fluxos existentes estão cobertos.

### Fase 5 — Entrega (deploy/rollback)

1. Atualizar docs/api.md, docs/database.md, documentação operacional e README com
   retry, estados, novos atributos, fila de exclusão e rollback.
2. Registrar o ADR, migration, contrato de mensagens e procedimento de recuperação de
   mensagens/objetos sem apagar volumes ou dados como atalho.
3. Executar git diff --check, build, testes prioritários, regressão completa e
   inspeção para garantir que o frontend não contém credenciais ou endpoints de
   infraestrutura.
4. Executar smoke test do Hub: rota inicial, upload, listagem, status, retry, erro,
   exclusão manual e confirmação de que os fluxos individuais continuam funcionando.
5. Fazer a revisão de convergência Spec ↔ Plano/Tarefas ↔ Código ↔ Testes e obter a
   aprovação final de Marcos antes de qualquer deploy.

Gate da Fase 5: documentação, migration, ADR, builds, testes, smoke test e rollback
revisados; nenhum deploy é executado sem aprovação final.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece ADR? |
|---|---|---|---|---|
| Retry concorrente pode publicar duas mensagens para o mesmo documento. | Alta | Alto | Lock pessimista, rejeição enquanto PROCESSING, publisher confirm e teste concorrente. | Não; coberto pelo contrato e testes. |
| Falha entre persistir isExcluded e publicar a mensagem de exclusão. | Alta | Alto | Persistir exclusionScheduledAt, publisher confirm, recuperação de documentos vencidos e fila durável. | Sim — ADR do fluxo de exclusão. |
| TTL de três horas pode ser perdido ou interpretado de forma diferente pelo broker. | Média | Alto | Declarar TTL na topologia, testar configuração e manter timestamp persistido para auditoria/recovery. | Sim — ADR do fluxo de exclusão. |
| Exclusão automática é irreversível e pode correr com exclusão manual. | Alta | Alto | Lock/idempotência, confirmação manual existente, consumidor tolerante a documento ausente e smoke test controlado. | Sim — ADR-006 ou novo ADR do Hub. |
| Migration pode atribuir valores incorretos a documentos já existentes. | Média | Alto | Migration expand-only, defaults explícitos, teste PostgreSQL e rollback operacional sem remoção direta. | Não; procedimento de migration. |
| Nova API de retry pode divergir do frontend ou expor detalhes de storage. | Média | Alto | Contrato HTTP antes do código, ProblemDetail estável e testes de controller/HTTP. | Não. |
| Mudança da rota / pode quebrar links e o fluxo de upload atual. | Média | Médio | Preservar rotas antigas, testar deep links e manter acesso direto a /documents/new. | Não. |
| Lista inteira no frontend pode crescer sem limite. | Média | Médio | Reutilizar o contrato atual nesta feature e manter paginação/filtros como feature separada. | Não. |

## 7. Perguntas abertas antes de começar

- A Spec da feature e este plano foram aprovados por Marcos antes da implementação.
- Decisão aprovada: o ADR deve registrar o mapeamento falhas → failure_count,
  isExcluded → is_excluded e exclusionScheduledAt → exclusion_scheduled_at, com
  failure_count BIGINT, is_excluded boolean e exclusion_scheduled_at TIMESTAMPTZ.
- Decisão aprovada: a topologia usa exchange docflow.document-deletion, fila de espera
  docflow.document-deletion.delay.3h, fila consumida
  docflow.document-deletion.requested, routing keys document.deletion.scheduled e
  document.deletion.requested, DLX docflow.document-deletion.dlx e DLQ
  docflow.document-deletion.dlq.
- O ADR-007 ainda deve ser criado e aprovado antes da implementação. Os nomes de
  infraestrutura não são requisitos de produto e não devem aparecer no frontend.

