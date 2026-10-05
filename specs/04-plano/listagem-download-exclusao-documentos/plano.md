# Plano de Implementação — Busca, filtros, paginação, ordenação, download em lote e exclusão em lote

Spec relacionada: `specs/03-features/listagem-download-exclusao-documentos/spec.md`  
Status: Rascunho — aguardando aprovação de Marcos

## 1. Resumo técnico

Ampliar o caso de uso existente de listagem no domínio `document`, mantendo
`GET /documents` como endpoint oficial e adicionando query parameters para busca por
`originalFilename`, filtros por `status`, `contentType` e data, paginação de dez itens
e ordenação pelos campos exibidos.

O backend continuará aplicando a consulta e retornando os metadados necessários para a
interface exibir a página atual e o total de páginas no formato X/Y. O frontend Angular
enviará os critérios, preservará a seleção entre mudanças de página/consulta e
apresentará as ações em lote.

O download em lote será uma única operação que produz um arquivo compactado contendo
somente documentos `COMPLETED`, transmitido sem carregar o lote inteiro em memória. A
exclusão em lote será uma única requisição com vários IDs, aceitará documentos em
qualquer estado e poderá retornar sucesso parcial com falha individual por item.

A exclusão continuará usando a política de exclusão definitiva do ADR-006: lock da
linha, remoção idempotente do storage final e staging, remoção posterior do registro e
tratamento de mensagens RabbitMQ que chegarem depois da exclusão. Nenhum novo domínio,
módulo global, acesso direto ao MinIO ou contrato de mensageria será criado.

As decisões de produto e contrato estão consolidadas na seção 7: parâmetros oficiais,
busca `contains`, filtros em ambas as datas, paginação, ordenação, ZIP, rotas de lote,
sucesso parcial e o header de itens ignorados durante o streaming. A Fase 1 deve
registrar esses contratos na documentação da API e formalizar a extensão do ADR-006.
Nenhuma tarefa de lógica ou interface deve começar antes desse registro.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `backend/.../document/controller/DocumentController.java` | Ampliar `GET /documents` com query parameters e adicionar os endpoints de download e exclusão em lote conforme contratos aprovados. Preservar `GET /documents/{documentId}` e o download/exclusão individuais. | Alto: mudança de resposta da coleção e conflito potencial com rotas existentes. |
| `backend/.../document/DocumentService.java` | Orquestrar consulta paginada, geração do ZIP e exclusão em lote, delegando regras e portas ao limite de aplicação. | Alto: download envolve vários streams; exclusão cruza PostgreSQL, MinIO, staging e mensagens assíncronas. |
| `backend/.../document/DocumentRepository.java` e adaptador de persistência | Adicionar consulta paginada com busca, filtros e ordenação permitidos; manter `findById`, `findAll` e locks usados pelos fluxos atuais. | Alto: consulta incorreta pode omitir documentos, quebrar ordenação ou causar regressão na listagem sem parâmetros. |
| `backend/.../document/dto/`, `adapter/in/web/dto/` e mappers existentes | Criar DTOs de critérios, página, requisição em lote e resultado por item, sem expor `objectKey` ou referências internas. | Médio: divergência entre contrato HTTP, frontend e testes. |
| `backend/.../document/storage/` e portas provider-neutral | Reutilizar leitura em streaming e exclusão existentes; adicionar apenas a abstração mínima necessária para processar vários conteúdos no ZIP. | Alto: uma implementação que usa `byte[]` para o lote pode consumir memória excessiva ou expor o SDK MinIO. |
| `backend/src/main/resources/db/migration/` | Nenhuma migration prevista; os campos necessários já existem. | Baixo inicialmente; uma necessidade real de índice/coluna deve interromper a implementação e voltar para decisão/migration. |
| `frontend/src/app/document/document-api.service.ts` e modelos | Enviar query parameters para `GET /documents`, interpretar metadados de página e consumir as operações em lote. Preservar upload, detalhe, download e exclusão individuais. | Alto: incompatibilidade de tipos ou rotas pode quebrar a tela existente. |
| `frontend/src/app/document/pages/document-list-page.*` | Adicionar controles de busca, filtros, ordenação, paginação, seleção persistente e ações em lote. | Alto: estado de seleção pode ser perdido ou produzir ações sobre itens fora da consulta atual. |
| `backend/src/test/...`, `frontend/src/app/document/*.spec.ts` e integração | Expandir cobertura unitária, HTTP, storage, concorrência e fluxo ponta a ponta. | Médio: testes podem mascarar falhas parciais se verificarem somente o status geral. |
| `docs/`, contrato da API, ADR-006 e checklist de convergência | Documentar parâmetros, ZIP, sucesso parcial, política para todos os estados, rollback e evidências. | Médio: documentação divergente impede a aprovação do gate e gera retrabalho. |

Não haverá reorganização estrutural. As classes existentes que permanecem diretamente
em `document/` continuarão nesse local; novos componentes serão colocados nas áreas de
aplicação, adaptadores, portas e entrada web já previstas pela arquitetura.

## 3. Componentes novos

- Critério de consulta de documentos no limite `document/application/` e DTOs de entrada
  e saída no adaptador web existente, contendo busca, filtros, página e ordenação.
- Resposta paginada da listagem com itens e metadados necessários para exibir página
  atual, total de páginas e tamanho fixo de dez itens.
- Consultas no repositório/adaptador de persistência com whitelist dos campos
  ordenáveis, filtro case-insensitive por `originalFilename` e combinação AND dos
  filtros preenchidos.
- Contrato HTTP da operação de download em lote e serviço de geração/transmissão de
  arquivo compactado, reutilizando a porta de leitura provider-neutral do storage.
- Contrato HTTP da operação de exclusão em lote, resultado por documento e mapeamento
  de sucesso total, sucesso parcial e falha.
- Estado de seleção e modelos de consulta/paginação no frontend, preservando a seleção
  quando a consulta visível mudar.
- Testes específicos para os contratos, sem criar uma biblioteca ou pasta compartilhada
  sem reuso confirmado por mais de uma feature.

Não será criado um módulo de mensageria novo. A exclusão em lote reutilizará o
consumidor, a DLQ e a regra já definida no ADR-006 para mensagens que chegarem após a
remoção do documento.

## 4. Mudança de dados/banco

Não há migration prevista. Busca, filtros, paginação e ordenação usam os campos já
persistidos: `originalFilename`, `contentType`, `status`, `sizeBytes`, `createdAt` e
`updatedAt`.

A implementação deve usar consultas paginadas do repositório e whitelist de ordenação,
sem montar SQL com nomes de campo recebidos livremente. Índices adicionais não serão
criados por antecipação. Se testes ou medição demonstrarem necessidade de índice ou
qualquer alteração de schema, a implementação deve parar, atualizar a Spec/plano e
criar migration Flyway com procedimento operacional de reversão antes de continuar.

A exclusão em lote não cria estado `DELETING`, tombstone, coluna de histórico ou nova
referência de staging. Ela segue o ADR-006: lock, remoção idempotente de objetos,
remoção do registro e tratamento de mensagem tardia pela DLQ.

## 5. Sequência de implementação

### Fase 1 — Base

1. Montar a matriz `FR/AC → contrato → código → teste` a partir da Spec aprovada e
   registrar o baseline de `POST /documents`, `GET /documents/{documentId}`, download
   individual, exclusão individual e listagem sem parâmetros.
2. Fechar o contrato de `GET /documents`: nomes e tipos dos query parameters, busca por
   `originalFilename`, filtros `status`/`contentType`/data, valores vazios ignorados,
   página de dez itens, metadados X/Y, campos ordenáveis, direção padrão, desempate,
   timezone e validação de valores inválidos.
3. Fechar o contrato do download em lote: requisição única, IDs selecionados, formato
   e nome do arquivo compactado, headers, streaming, comportamento para itens não
   `COMPLETED`/ausentes/indisponíveis e códigos de erro.
4. Fechar o contrato da exclusão em lote: requisição única com vários IDs, resposta
   por item, status HTTP, semântica de sucesso parcial, mensagens padrão e comportamento
   para documentos em todos os estados.
5. Revisar e, se necessário, estender o ADR-006 para explicitar como a operação em lote
   mantém lock, idempotência, remoção de staging/final, corrida com consumidor e
   processamento de resultado parcial. Se a decisão mudar estado, schema ou mensagem,
   abrir um novo registro antes da Fase 2.
6. Preparar fixtures e testes de contrato para os quatro estados, campos de listagem,
   conteúdo disponível, documentos ausentes e falhas de storage, preservando os testes
   dos fluxos existentes.

**Gate da Fase 1:** contratos HTTP e internos aprovados; perguntas abertas de produto
que alteram comportamento resolvidas; ADR-006 revisado; sem ambiguidade sobre ZIP,
sucesso parcial, seleção persistente, documentos `PENDING`/`PROCESSING` e rollback.

### Fase 2 — Lógica principal

1. Implementar no repositório a consulta paginada de `GET /documents`, com busca
   case-insensitive por `originalFilename`, filtros AND, filtros vazios ignorados,
   página fixa de dez itens e whitelist de todos os campos exibidos para ordenação.
2. Implementar no serviço e controller o mapeamento dos query parameters, resposta
   paginada, metadados X/Y e erros determinísticos, mantendo a resposta e o
   comportamento compatíveis quando não houver critérios.
3. Implementar o caso de uso de download em lote em streaming, abrindo somente os
   conteúdos `COMPLETED`, adicionando-os ao arquivo compactado e fechando cada stream
   mesmo em falha; não carregar o lote inteiro em memória nem expor `objectKey`.
4. Implementar a validação de disponibilidade do download em lote e o resultado de
   falhas, sem incluir silenciosamente documentos não `COMPLETED` ou ausentes.
5. Implementar a exclusão em lote como uma única operação de entrada, delegando cada
   documento à política aprovada do ADR-006 e retornando o resultado real por item.
   A operação deve preservar registros não removidos, ser idempotente conforme a
   decisão e impedir processamento posterior indevido pelo consumidor.
6. Implementar os mapeamentos de erro e sucesso parcial na API sem alterar os contratos
   de upload, consulta por ID, download individual ou exclusão individual.
7. Implementar no `DocumentApiService` os métodos de consulta com query parameters,
   download compactado e exclusão em lote, com modelos tipados para página, seleção e
   resultado por item.

**Gate da Fase 2:** backend e serviço HTTP frontend exercitam os contratos aprovados;
consulta e paginação são server-side; ZIP é streaming; exclusão parcial não mascara
falhas; fluxos existentes continuam verdes.

### Fase 3 — Interface

1. Adicionar à página existente de documentos o campo de busca por filename, filtros de
   status/contentType/data e controles de ordenação para todos os campos exibidos.
2. Adicionar paginação de dez itens e indicador de página atual/total no formato X/Y,
   preservando estados de carregamento, vazio, erro e tentativa novamente.
3. Adicionar seleção individual e seleção em massa, mantendo a seleção ao trocar de
   página, busca, filtro ou ordenação e sem aplicar limite máximo definido pelo produto.
4. Adicionar a ação de download em lote, mantendo-a vinculada aos itens selecionados e
   apresentando o arquivo compactado recebido pelo backend; tratar itens não
   disponíveis com a mensagem padrão aprovada.
5. Adicionar confirmação explícita para exclusão em lote, enviar uma única requisição,
   remover somente itens confirmados e apresentar mensagens individuais no sucesso
   parcial.
6. Revisar acessibilidade, foco, teclado, responsividade, estados concorrentes e
   coexistência com `documents`, `documents/new` e `documents/:documentId`.

**Gate da Fase 3:** a interface cobre consulta combinada, navegação X/Y, seleção
persistente, ZIP, confirmação/cancelamento, sucesso parcial e erros sem regressão
visual ou de navegação.

### Fase 4 — Testes

1. Cobrir a consulta do repositório e serviço com filename case-insensitive, filtros
   AND, filtros vazios ignorados, data, dez itens por página, metadados X/Y, todos os
   campos ordenáveis e validação de parâmetros inválidos.
2. Cobrir o controller e o cliente HTTP com os query parameters oficiais, resposta
   paginada, lista vazia, erro de consulta e preservação dos contratos existentes.
3. Cobrir o download em lote com ZIP válido, somente `COMPLETED`, conteúdo correto,
   headers, streaming, fechamento de streams, ausência de `objectKey` e falhas por item.
4. Cobrir a exclusão em lote com todos os estados, lock, remoção final/staging,
   idempotência, falha parcial, documento ausente, corrida com consumidor e mensagem
   tardia encaminhada à DLQ conforme ADR-006.
5. Cobrir os componentes Angular com consulta combinada, paginação, seleção persistente,
   download ZIP, seleção vazia, confirmação/cancelamento e resultado parcial.
6. Executar integração ponta a ponta com PostgreSQL, MinIO, RabbitMQ e frontend,
   incluindo os ACs da Spec e regressão dos fluxos de upload, detalhe, download
   individual e exclusão individual.

**Gate da Fase 4:** AC-001 a AC-010 passam com evidência automatizada ou de integração;
falhas parciais são verificadas por item; a suíte existente permanece verde.

### Fase 5 — Entrega (deploy/rollback)

1. Atualizar documentação da API, frontend, query parameters, paginação, ZIP, exclusão
   parcial, mensagens padrão, ADR e procedimento local.
2. Executar build do backend e frontend, testes prioritários e inspeção para garantir
   que o frontend não contenha credenciais, `objectKey`, URLs de MinIO, RabbitMQ ou
   conexões de banco.
3. Executar smoke test local cobrindo busca case-insensitive, cada filtro, consulta
   combinada, página X/Y, ordenação, ZIP somente `COMPLETED`, exclusão total/parcial,
   documentos `PENDING`/`PROCESSING` e regressão dos fluxos individuais.
4. Registrar rollback de código e limites operacionais: exclusões e objetos removidos
   não são recuperados por rollback de versão; a reversão não deve apagar volumes,
   mensagens ou objetos sem procedimento explícito.
5. Executar a revisão de convergência `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes` e
   aguardar a aprovação final de Marcos antes de qualquer deploy.

**Gate da Fase 5:** documentação, builds, testes, smoke test, ADR, riscos e rollback
revisados; nenhum deploy executado neste plano sem aprovação final.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| Alterar `GET /documents` pode quebrar o frontend ou consumidores da listagem sem parâmetros. | Alta | Alto: regressão de consulta e navegação. | Manter compatibilidade sem critérios, definir contrato antes do código, testar resposta paginada e executar regressão. | Não; cobrir no contrato e nos testes. |
| Busca case-insensitive, data e ordenação podem divergir por collation, timezone ou campo escolhido. | Média | Alto: resultados incorretos e difíceis de reproduzir. | Fechar semântica na Fase 1, usar whitelist, testes com acentos/case, datas de limite e timezone documentado. | Não, salvo mudança de schema/infraestrutura. |
| Arquivo ZIP grande pode consumir memória, tempo ou conexões de storage. | Alta | Alto: timeout, erro parcial ou indisponibilidade. | Streaming incremental, fechamento de cada stream, testes com múltiplos tamanhos e limite operacional explicitamente aprovado. | Não; é contrato/implementação, salvo mudança arquitetural. |
| Seleção persistente sem limite pode gerar uma requisição ou ZIP muito grande. | Média | Alto: degradação do frontend/backend e operação longa. | Usar IDs, não blobs, processamento streaming, telemetria, validação operacional e registrar qualquer limite futuro como mudança de Spec. | Não inicialmente; sim se for necessário alterar o requisito de “sem limite”. |
| Exclusão em lote cruza PostgreSQL, MinIO, staging e RabbitMQ sem transação distribuída. | Alta | Alto: registros/objetos órfãos ou estado parcial. | Reutilizar ADR-006, lock, ordem de remoção, idempotência, resultado por item, testes de falha e retry manual. | Sim — ADR-006 deve ser revisado ou complementado. |
| Exclusão de `PENDING`/`PROCESSING` pode correr com consumidor, retry ou reconciliação. | Alta | Alto: processamento ou recriação após exclusão. | Manter lock e política do ADR-006, tratar documento ausente como mensagem inválida/DLQ e testar corrida real. | Sim — dentro do ADR-006 ou novo ADR se a política mudar. |
| Sucesso parcial pode ser interpretado pelo frontend como sucesso total. | Média | Alto: itens não removidos desaparecem da interface. | Contrato por item, testes de resposta parcial, atualização local somente dos confirmados e mensagem padrão individual. | Não; contrato deve ser aprovado na Fase 1. |
| Rota/verbos do lote podem conflitar com `/{documentId}` ou variar entre backend e frontend. | Média | Alto: 404, roteamento incorreto e retrabalho. | Definir rotas antes da implementação, declarar rotas específicas antes das parametrizadas e testar chamadas reais. | Não; é risco de contrato. |
| Ordenação recebida livremente pode permitir consulta inválida ou injeção de campo. | Baixa | Alto | Whitelist de todos os campos aprovados, rejeição determinística e testes de parâmetros inválidos. | Não. |
| Exclusão física é irreversível após a remoção dos objetos. | Alta | Alto: perda definitiva de conteúdo. | Confirmação explícita, logs, smoke test controlado, rollback somente de código e aprovação final antes de deploy. | Sim — consequência operacional do ADR-006. |

## 7. Respostas às perguntas abertas antes de começar

### 7.1 Pontos já respondidos pela documentação e pela arquitetura

| Pergunta | Resposta sustentada | Fonte |
|---|---|---|
| Qual endpoint recebe busca, filtros, paginação e ordenação? | `GET /documents` continua sendo o endpoint oficial; os critérios serão query parameters. | Spec aprovada e contrato atual em `docs/api.md`. |
| Onde a consulta é executada? | No backend, através do serviço de aplicação e do repositório; o frontend apenas envia critérios e exibe a resposta. | `specs/02-arquitetura/ARQUITETURA.md`, limites de API/aplicação e Spec aprovada. |
| Quais filtros e combinação? | `status`, `contentType` e data; filtros preenchidos combinados por AND; filtros vazios não participam. | Spec aprovada. |
| Qual página? | Dez documentos por página; a interface exibe página atual e total no formato X/Y. | Spec aprovada. |
| Quais campos podem ser ordenados? | Todos os campos exibidos: `id`, `originalFilename`, `contentType`, `sizeBytes`, `status`, `createdAt` e `updatedAt`. | `DocumentResponse`, `docs/api.md` e Spec aprovada. |
| Qual regra da busca? | Busca em `originalFilename`, sem diferenciação entre maiúsculas e minúsculas. | Spec aprovada. |
| Quais estados aparecem na consulta? | `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`; filtros podem restringir estados. | `DocumentStatus`, `docs/api.md` e Spec aprovada. |
| Qual envelope de erro? | O padrão existente é `application/problem+json` com `ProblemDetail`, `errorCode` e `invalidField` quando aplicável. | `docs/api.md`, `GlobalExceptionHandler` e `document.models.ts`. |
| Como deve ser o streaming? | O conteúdo deve ser lido por porta provider-neutral, sem `byte[]` obrigatório; o consumidor do stream deve fechá-lo inclusive em falha. | `docs/api.md`, arquitetura e contrato de download individual. |
| Quais headers orientam o download? | O contrato individual usa `Content-Type`, `Content-Length`, `Content-Disposition: attachment` e `Cache-Control: no-store`; o lote deve manter a mesma política de download seguro, adaptada a `application/zip`. | `docs/api.md` e `docs/frontend.md`. |
| Como tratar documentos `PENDING`/`PROCESSING` na exclusão? | Eles podem ser excluídos; a operação deve usar lock, remover storage final/staging antes do registro, ser idempotente e encaminhar mensagem tardia ao destino definido, sem cancelar RabbitMQ. | ADR-006. |
| Há exclusão lógica ou novo estado? | Não. A exclusão é física e definitiva; não criar `DELETING`, tombstone ou histórico de lixeira. | ADR-006 e arquitetura. |
| Haverá migration? | Não há mudança de schema prevista. Se índice, coluna, estado ou referência nova se tornar necessária, a implementação deve parar e abrir migration/decisão antes de continuar. | Seção 4 deste plano, ADR-006 e limites arquiteturais. |
| Como o frontend mostra erros existentes? | Falhas de listagem usam `Não foi possível exibir itens listados`; falhas operacionais usam mensagens genéricas com possibilidade de nova tentativa; lista vazia usa `Nenhum documento encontrado`. | `document-error.mapper.ts`, `document.models.ts` e `docs/frontend.md`. |

### 7.2 Decisões consolidadas a partir das respostas e dos padrões disponíveis

As decisões abaixo incorporam as respostas de Marcos e usam as convenções já presentes
no projeto. T-002, T-003 e T-004 devem apenas registrar os contratos finais antes do
código.

1. **Query parameters oficiais:**

   ```text
   GET /documents?
     search=<termo>&
     status=<status>&
     contentType=<mime>&
     createdFrom=<instant>&
     createdTo=<instant>&
     updatedFrom=<instant>&
     updatedTo=<instant>&
     page=<numero>&
     sort=<campo>,<direcao>
   ```

   `search` representa busca parcial (`contains`) case-insensitive em
   `originalFilename`. Os filtros de data cobrem tanto `createdAt` quanto `updatedAt`.
   `sort=campo,direcao` segue a convenção do ecossistema Spring Data. Valores vazios
   são omitidos; os parâmetros oficiais aceitam uma condição por campo nesta versão.

2. **Numeração de página:** usar página externa 1-based para corresponder ao “X/Y” da
   interface e converter internamente para o índice 0-based do Spring Data. O tamanho
   é fixo em dez itens e não será exposto como parâmetro nesta versão.

3. **Formato da resposta paginada:** usar um envelope JSON estável, sem expor
   diretamente o objeto Spring `Page`, com a estrutura:

   ```json
   {
     "content": [],
     "page": 1,
     "size": 10,
     "totalElements": 0,
     "totalPages": 0
   }
   ```

   Isso preserva os sete campos do item e fornece exatamente os dados necessários para
   X/Y. O campo `page` é 1-based para o cliente.

4. **Ordenação padrão:** `updatedAt,desc`, para exibir primeiro o documento mais
   recentemente atualizado; em empate, `createdAt,desc` e depois `id,asc` para manter
   uma ordem determinística. O usuário pode ordenar qualquer campo exibido.

5. **Filtro de data:** filtrar `createdAt` e `updatedAt` com intervalos semiabertos
   `[from,to)`, usando instants ISO-8601 em UTC. A interface converte a data civil do
   usuário para UTC antes de montar a consulta, evitando ambiguidade de horário de verão
   e mantendo compatibilidade com os campos Java `Instant`.

6. **Download ZIP:** usar `application/zip`,
   `Content-Disposition: attachment; filename="documents.zip"` e
   `Cache-Control: no-store`, mantendo streaming e omitindo `Content-Length` quando ele
   não puder ser calculado sem materializar o arquivo. Somente itens `COMPLETED` entram
   no ZIP. Itens selecionados em outro estado são ignorados e o frontend informa ao
   usuário quais não foram baixados; o backend também valida o estado para proteger
   contra mudanças ocorridas entre a listagem e a requisição.

7. **Rotas de lote:** usar uma requisição com corpo para não colocar uma lista
   potencialmente grande de IDs na URL: `POST /documents/batch-download` com
   `{ "documentIds": [...] }` e `DELETE /documents` com
   `{ "documentIds": [...] }`. `DELETE /documents/{documentId}` permanece a operação
   individual.

8. **Resposta da exclusão em lote:** usar `200 OK` com resultado por item, inclusive no
   sucesso total, porque `204 No Content` não permite transportar o sucesso parcial. A
   estrutura é:

   ```json
   {
     "results": [
       { "documentId": "...", "status": "DELETED" },
       { "documentId": "...", "status": "FAILED", "errorCode": "DOCUMENT_DELETION_FAILED", "message": "..." }
     ]
   }
   ```

   Falha de requisição inteira usa `ProblemDetail` em `application/problem+json`;
   falhas por item usam os códigos já existentes (`DOCUMENT_STORAGE_UNAVAILABLE`,
   `DOCUMENT_DELETION_FAILED` e `DOCUMENT_NOT_FOUND`) dentro de `results`. Não será
   usado `207`; `200` mantém um contrato único para sucesso total e parcial.

### 7.3 Detalhe técnico resolvido

- A busca `contains` seguirá a comparação case-insensitive padrão do PostgreSQL/JPA;
  normalização adicional de acentos não foi solicitada e não será criada nesta rodada.
- Se um documento mudar de `COMPLETED` para outro estado depois da seleção e antes do
  download, o backend o omitirá do ZIP e devolverá os IDs ignorados no header
  `X-DocFlow-Skipped-Documents`, separados por vírgula. Se nenhum documento puder ser
  incluído, a resposta será `409` com `ProblemDetail` e a propriedade
  `skippedDocumentIds`; não será gerado ZIP vazio. O frontend correlacionará os IDs aos
  nomes exibidos para informar o usuário.

Com essas respostas, as decisões de produto e de contrato do plano estão fechadas o
suficiente para a aprovação. O gate da Fase 1 registra os contratos na documentação da
API e no ADR-006 antes do código, sem inventar comportamento adicional.
