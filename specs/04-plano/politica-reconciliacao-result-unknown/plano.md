# Plano de Implementação — Política de reconciliação para resultado desconhecido do storage (`RESULT_UNKNOWN`)

Spec relacionada: specs/03-features/politica-reconciliacao-result-unknown/spec.md  
Status: Rascunho

## 1. Resumo técnico

A implementação adicionará um caso de uso de reconciliação na camada de aplicação do
domínio `document`. Esse caso de uso carregará um documento em `PROCESSING`, verificará
somente `DocumentStorage.exists(objectKey)` e persistirá a transição de estado pelo
domínio: `COMPLETED` quando o objeto existir ou `FAILED` quando o limite de tentativas
for atingido sem confirmação.

O contador de tentativas será persistido no PostgreSQL por uma migration Flyway `V4`.
Não serão criados nesta feature o componente que dispara a reconciliação, scheduler,
fila RabbitMQ, endpoint REST ou limpeza de objetos.

A implementação usará o limite de feature `document` e as responsabilidades já definidas
em `specs/02-arquitetura/ARQUITETURA.md`: aplicação coordena, domínio controla estados,
persistência acessa PostgreSQL e storage é acessado pela porta `DocumentStorage`. A
organização das classes novas seguirá os subpacotes arquiteturais existentes no desenho
alvo; não será criado módulo ou estrutura paralela.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/src/main/java/com/dockflow/dockflow/document/Document.java` | Expor o estado necessário para controlar tentativas e preservar as transições de domínio; não adicionar novo status. | Médio: alterações nas invariantes podem afetar testes e transições existentes. |
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentService.java` ou equivalente na camada `document/application` | Integrar o novo caso de uso de reconciliação sem alterar registro e consulta existentes. | Médio: limite transacional e chamadas existentes não podem regredir. |
| `backend/src/main/java/com/dockflow/dockflow/document/DocumentRepository.java` ou adaptador de persistência equivalente | Ler e salvar contador, estado e referência do documento. | Médio: mapeamento JPA e concorrência podem causar atualização perdida. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/DocumentStorage.java` | Reutilizar `exists(String objectKey)` sem alterar o contrato. | Baixo: o método já existe e é o contrato provider-neutral previsto. |
| `backend/src/main/java/com/dockflow/dockflow/document/storage/minio/MinioDocumentStorage.java` | Reutilizar `exists`, que já usa `statObject`; não repetir a gravação nem mudar o mapeamento existente. | Baixo: mudanças desnecessárias poderiam afetar a integração MinIO. |
| `backend/src/main/resources/db/migration/V1` a `V3` | Não alterar migrations já aplicadas. | Baixo: preservar histórico e reprodutibilidade do Flyway. |
| `backend/src/test/java/com/dockflow/dockflow/document/` | Ampliar testes unitários e de integração e executar a regressão existente. | Baixo: novos cenários devem manter os testes atuais válidos. |

As classes novas seguirão a organização por domínio, aplicação, portas e adaptadores
definida pelo ADR-003. A migração geral de classes antigas que não forem necessárias para
esta feature fica fora deste plano, para evitar uma refatoração estrutural sem requisito
funcional correspondente.

## 3. Componentes novos

- Caso de uso/serviço de aplicação para reconciliar um documento em `PROCESSING`, dentro
  de `document/application` conforme a arquitetura-alvo.
- Estado persistido para o contador de tentativas, implementado por migration `V4` e seu
  mapeamento JPA correspondente.
- Testes unitários do caso de uso e testes de integração da migration e da persistência.
- Teste de contrato do uso de `DocumentStorage.exists`, se a cobertura atual do adaptador
  não comprovar os retornos `true` e `false`.

Não serão criados controller, DTO REST, publisher/consumer RabbitMQ, scheduler, novo
adaptador de storage ou rotina de `delete(objectKey)` nesta rodada.

## 4. Mudança de dados/banco (se houver)

Será criada uma nova migration:

```text
backend/src/main/resources/db/migration/V4__add_reconciliation_attempts.sql
```

A migration deve adicionar a persistência do contador de tentativas sem alterar os dados
existentes diretamente e sem editar `V1`, `V2` ou `V3`. A opção mínima recomendada para
esta feature é uma nova coluna na tabela `documents`, pois o requisito atual é somente
controlar o contador; uma tabela separada fica reservada para o caso de histórico completo
ser aprovado.

O rollback operacional deve ser feito com código compatível com o schema anterior e
estratégia de avanço controlado. Não será presumida uma migration destrutiva de retorno,
porque remover a coluna ou a tabela pode eliminar informação de reconciliação.

Antes da implementação, a decisão entre coluna em `documents` e tabela separada precisa
ser confirmada caso o histórico completo seja necessário. A escolha de uma tabela de
histórico muda o modelo de persistência e pode merecer um Registro de Decisão.

## 5. Sequência de implementação

### Fase 1 — Base

1. Confirmar o formato persistido do contador e preparar a migration `V4`.
2. Preparar o mapeamento de persistência e o ponto de entrada da aplicação, preservando
   `DocumentStorage.exists` e as classes existentes fora do escopo.
3. Confirmar como o `objectKey` estará disponível antes de `COMPLETED`, seguindo a decisão
   arquitetural de chave baseada no UUID do documento.

**Saída da fase:** schema e contratos internos definidos, sem scheduler, fila ou API nova.

### Fase 2 — Lógica principal

1. Implementar a reconciliação usando somente `exists(objectKey)`.
2. Manter `PROCESSING` quando a existência não for confirmada e ainda houver tentativas.
3. Persistir o contador e mover para `FAILED` ao esgotar as tentativas.
4. Mover para `COMPLETED` somente depois de `exists(objectKey)` retornar confirmação.

**Saída da fase:** FR-001 a FR-005 atendidos pelo caso de uso, sem acoplamento ao SDK do
MinIO.

### Fase 3 — Interface

Não haverá interface externa nesta feature. Será disponibilizado somente um ponto de
entrada interno na camada de aplicação para que outra feature possa acionar a
reconciliação posteriormente. O mecanismo disparador e sua frequência serão definidos em
outra Spec.

**Saída da fase:** nenhum endpoint, contrato público, fila ou scheduler foi introduzido.

### Fase 4 — Testes

1. Cobrir `RESULT_UNKNOWN` mantendo `PROCESSING`.
2. Cobrir confirmação por `exists(objectKey)` levando a `COMPLETED`.
3. Cobrir ausência de confirmação com tentativas restantes mantendo `PROCESSING`.
4. Cobrir esgotamento levando a `FAILED`.
5. Verificar a migration `V4`, persistência do contador e não regressão das funcionalidades
   atuais.

### Fase 5 — Entrega (deploy/rollback)

1. Executar a suíte indicada pelo plano de verificação do projeto.
2. Validar a ordem das migrations e o comportamento com documentos existentes.
3. Revisar o diff, o procedimento de rollback operacional e os riscos abertos.
4. Aguardar a aprovação de Marcos antes de qualquer deploy.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| Escolher coluna em `documents` ou tabela separada para o contador/histórico. | Média | Alto: pode exigir migração ou backfill posterior. | Implementar somente o contador mínimo se histórico não for aprovado; registrar decisão antes de adotar tabela separada. | Sim, se o histórico completo for incluído; não para uma coluna simples de contador. |
| O `objectKey` atual só é atribuído em `markCompleted`, embora a reconciliação precise verificá-lo antes disso. | Média | Alto: pode impedir a chamada correta a `exists(objectKey)` ou gerar referência inconsistente. | Derivar/persistir a chave determinística baseada no UUID conforme ADR-001; se a estratégia mudar, criar ADR antes. | Não, enquanto seguir ADR-001; sim se a estratégia de chave for alterada. |
| Duas reconciliações concorrentes podem incrementar tentativas ou aplicar transições conflitantes. | Média | Médio/alto: contador incorreto ou atualização perdida. | Usar o controle de versão já existente, manter a operação transacional e testar concorrência/atualização otimista. | Não inicialmente; criar ADR se for necessária uma estratégia de locking ou idempotência que altere o contrato. |
| A migration `V4` pode afetar documentos existentes ou dificultar rollback. | Baixa/média | Alto: falha de inicialização ou perda do estado de reconciliação. | Migration expand-only, valor compatível para registros existentes, teste com Testcontainers e rollback operacional por código compatível. | Não; o padrão Flyway já está definido pela arquitetura. |
| `exists` pode retornar resultado inconclusivo durante a própria verificação. | Média | Médio: tratar inconclusivo como ausência pode consumir tentativas indevidamente. | Definir explicitamente esse caso antes da implementação e cobrir o mapeamento de `DocumentStorageException`. | Não, salvo se exigir mudança no contrato da porta. |

## 7. Perguntas abertas antes de começar

- Qual é o número máximo de tentativas que caracteriza “esgotar as tentativas”? Essa
  definição é necessária para implementar FR-004, embora o disparo e a frequência da
  reconciliação permaneçam em outra Spec.
- O contador será uma coluna em `documents` ou haverá uma tabela separada para histórico
  completo? A tabela separada exige decisão explícita e pode merecer ADR.
- O `objectKey` poderá ser derivado do UUID do documento antes de `COMPLETED`, conforme
  ADR-001, ou deverá ser persistido em outra etapa do fluxo de gravação?
- Se `exists(objectKey)` retornar `RESULT_UNKNOWN` durante a verificação, isso deve ser
  contabilizado como tentativa sem confirmação e seguir aguardando novas tentativas?
- Qual plano de verificação substituirá ou complementará o caminho referenciado no
  template (`specs/05-verificacao/plano-de-teste-template.md`) quando esta feature for
  implementada?
