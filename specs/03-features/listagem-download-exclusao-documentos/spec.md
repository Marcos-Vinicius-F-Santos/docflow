# Spec da Feature — Busca, filtros, paginação, ordenação, download em lote e exclusão em lote

Status: Aprovada  
Projeto: DocFlow  
Onde vive no código: `backend/src/main/java/com/dockflow/dockflow/document/` e
`frontend/src/app/document/`, respeitando a organização definida em
`specs/02-arquitetura/ARQUITETURA.md`

## 1. Objetivo

Permitir que o usuário encontre documentos com busca, filtros, paginação e ordenação,
além de selecionar vários documentos para download ou exclusão em lote.

Esta feature amplia a listagem já existente e deve criar ou ampliar os contratos GET
necessários para que a consulta seja realizada de forma compatível entre frontend e
backend, sem alterar os fluxos existentes de upload, consulta por identificador,
download individual e exclusão individual.

## 2. Como funciona hoje

- O backend possui `GET /documents`, que retorna a coleção completa de documentos por
  meio de `findAll()`, sem parâmetros de busca, filtros, paginação ou ordenação.
- A resposta atual da listagem contém os campos `id`, `originalFilename`,
  `contentType`, `sizeBytes`, `status`, `createdAt` e `updatedAt`.
- O frontend chama `GET /documents`, mantém a coleção inteira em memória e exibe todos
  os itens recebidos em uma única lista.
- A listagem atual inclui documentos nos estados `PENDING`, `PROCESSING`, `COMPLETED` e
  `FAILED`.
- O usuário consegue baixar um documento por vez por `GET /documents/{documentId}/content`.
- O usuário consegue excluir um documento por vez por `DELETE /documents/{documentId}`,
  após confirmação explícita.
- O backend já possui `GET /documents/{documentId}` para consulta por identificador e
  `POST /documents` para registro; esses fluxos não devem ser alterados por esta
  feature.
- A feature solicitada muda o comportamento da listagem existente ao acrescentar
  critérios de consulta, navegação entre páginas e ações sobre múltiplos itens.

## 3. Requisitos funcionais

### FR-001

QUANDO o usuário informar um termo de busca  
O SISTEMA DEVE consultar o campo `originalFilename` sem diferenciar letras maiúsculas
de minúsculas e exibir somente os documentos compatíveis com o termo informado.

### FR-002

QUANDO o usuário aplicar um ou mais filtros disponíveis  
O SISTEMA DEVE permitir filtrar por `status`, `contentType` e data, combinando os
filtros informados; filtros vazios não devem participar da filtragem.

### FR-003

QUANDO o usuário navegar pela listagem  
O SISTEMA DEVE retornar dez documentos por página, quando houver itens suficientes, e
exibir a página atual e o total de páginas no formato equivalente a “página X/Y”.

### FR-004

QUANDO o usuário escolher um campo e uma direção de ordenação válidos  
O SISTEMA DEVE permitir ordenar por qualquer campo exibido na listagem e exibir os
documentos na ordem solicitada.

### FR-005

QUANDO o usuário combinar busca, filtros, paginação e ordenação  
O SISTEMA DEVE aplicar os critérios no backend, usando a combinação lógica AND entre
os filtros preenchidos, e o frontend DEVE exibir a resposta recebida.

### FR-006

QUANDO uma consulta de documentos for retornada com sucesso  
O SISTEMA DEVE preservar, para cada item, os campos já exibidos na listagem atual:
`id`, `originalFilename`, `contentType`, `sizeBytes`, `status`, `createdAt` e
`updatedAt`.

### FR-007

QUANDO o usuário selecionar documentos exibidos na listagem  
O SISTEMA DEVE permitir selecionar um ou mais itens para uma ação em lote, manter a
seleção durante a troca de página, busca, filtro ou ordenação e não impor limite máximo
de itens selecionados.

### FR-008

QUANDO o usuário solicitar o download em lote dos documentos selecionados  
O SISTEMA DEVE iniciar uma única operação de download em lote, entregando um arquivo
compactado com os documentos selecionados que estejam no estado `COMPLETED`.

### FR-009

QUANDO o usuário solicitar a exclusão em lote dos documentos selecionados  
O SISTEMA DEVE solicitar confirmação explícita antes de enviar a operação ao backend.

### FR-010

QUANDO o usuário confirmar a exclusão em lote  
O SISTEMA DEVE enviar uma única requisição contendo os documentos selecionados e só
deve removê-los da listagem depois de receber a confirmação correspondente do backend.

### FR-011

QUANDO o usuário cancelar a confirmação da exclusão em lote  
O SISTEMA NÃO DEVE enviar a operação de exclusão e DEVE manter os documentos
selecionados disponíveis na listagem.

### FR-012

QUANDO uma operação de consulta, download em lote ou exclusão em lote falhar  
O SISTEMA DEVE informar a falha usando as mensagens padrão da aplicação e NÃO DEVE
apresentar como bem-sucedidos os itens ou operações não confirmados.

### FR-013

QUANDO uma consulta não encontrar documentos compatíveis com a busca ou os filtros  
O SISTEMA DEVE exibir um estado de lista vazia sem apresentar documentos fora dos
critérios informados.

### FR-014

QUANDO o usuário consultar a listagem sem busca, filtro, paginação ou ordenação  
O SISTEMA DEVE preservar o acesso à coleção de documentos existente, respeitando o
contrato compatível que for aprovado para a nova consulta.

### FR-015

QUANDO a feature for implementada  
O SISTEMA DEVE preservar o funcionamento de `POST /documents`,
`GET /documents/{documentId}`, o download individual e a exclusão individual.

## 4. Regras de negócio

### BR-001

O backend continua sendo a fonte de verdade para os documentos retornados, para a
disponibilidade de seus conteúdos e para o resultado das exclusões.

### BR-002

Busca, filtros, paginação e ordenação devem respeitar os limites da API e da aplicação;
frontend, controller e serviço não devem acessar diretamente PostgreSQL, MinIO,
RabbitMQ ou seus SDKs.

### BR-003

A nova consulta deve continuar considerando os estados de documento já existentes
(`PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`), salvo filtro explícito aprovado para
restringir estados.

### BR-004

Uma seleção usada para download ou exclusão em lote deve conter apenas documentos que o
usuário consegue identificar na listagem atual.

### BR-005

A exclusão em lote mantém a natureza definitiva da exclusão individual: não há
exclusão lógica, e o registro persistido e o conteúdo associado devem seguir a política
aprovada para a exclusão de documentos. Documentos nos estados `PENDING` e
`PROCESSING` também podem ser selecionados, desde que a operação trate a corrida com
staging, RabbitMQ, retries e consumidor sem permitir recriação ou processamento
silencioso após a exclusão.

### BR-006

A confirmação explícita é obrigatória para exclusão em lote. Cancelar a confirmação não
pode causar qualquer exclusão.

### BR-007

O conteúdo de um documento só pode ser incluído no download em lote quando estiver
no estado `COMPLETED`. Documentos em `PENDING`, `PROCESSING`, `FAILED`, ausentes ou
indisponíveis no storage devem ser ignorados ou reportados conforme o resultado do lote
e não podem ser apresentados como incluídos com sucesso. O frontend deve informar os
itens ignorados ao usuário.

### BR-008

Uma falha parcial em download ou exclusão em lote não pode ser escondida como sucesso
total. A exclusão em lote pode retornar sucesso parcial, com mensagem padrão de falha
para cada item não removido; os itens não confirmados não devem ser removidos da
listagem.

### BR-009

Parâmetros inválidos de busca, filtro, paginação ou ordenação devem ser rejeitados ou
normalizados de maneira determinística pelo contrato da API, sem retornar uma página
incorreta silenciosamente.

## 5. Critério de aceite

### AC-001 — caminho feliz da consulta combinada

Dado que existem documentos com nomes, estados e datas diferentes  
Quando o usuário informar uma busca, aplicar filtros, escolher uma ordenação e
selecionar uma página válida  
Então o sistema deve consultar o backend e exibir somente os documentos compatíveis,
considerando `originalFilename` sem distinção entre maiúsculas e minúsculas, aplicando
os filtros preenchidos por AND, na ordem solicitada, respeitando dez itens por página e
exibindo a página no formato X/Y.

### AC-002 — caminho feliz da navegação entre páginas

Dado que a consulta possui documentos suficientes para mais de uma página  
Quando o usuário avançar ou retornar uma página  
Então o sistema deve solicitar a página correspondente e não deve misturar itens de
páginas diferentes na mesma exibição, mantendo a indicação da página atual e do total
de páginas.

### AC-003 — caminho feliz do download em lote

Dado que o usuário selecionou dois ou mais documentos com conteúdo disponível  
Quando o usuário solicitar o download em lote  
Então o sistema deve iniciar uma única operação e entregar um arquivo compactado com
os documentos selecionados no estado `COMPLETED`, informar quais itens selecionados
foram ignorados por não estarem disponíveis para download e não iniciar uma operação de
exclusão.

### AC-004 — caminho feliz da exclusão em lote

Dado que o usuário selecionou dois ou mais documentos  
Quando solicitar a exclusão em lote, confirmar explicitamente a ação e o backend
confirmar sucesso para todos os itens  
Então os documentos selecionados devem deixar de ser apresentados como disponíveis e
devem ser excluídos definitivamente conforme a política da exclusão individual.

### AC-005 — cancelamento da exclusão em lote

Dado que existem documentos selecionados para exclusão  
Quando o usuário cancelar a confirmação  
Então o sistema não deve enviar a operação de exclusão ao backend e deve manter os
documentos na listagem.

### AC-006 — consulta sem resultados

Dado que nenhum documento atende à busca ou aos filtros informados  
Quando o usuário executar a consulta  
Então o sistema deve exibir o estado de lista vazia e não deve apresentar documentos
que não atendam aos critérios.

### AC-007 — caminho de erro da consulta

Dado que o backend rejeita um parâmetro de consulta ou está indisponível  
Quando o usuário executar busca, filtro, paginação ou ordenação  
Então o sistema deve informar que a consulta não foi concluída, não deve apresentar a
resposta como atual e deve preservar uma forma de tentar novamente.

### AC-008 — caminho de erro do download em lote

Dado que pelo menos um documento selecionado não pode ser incluído no download  
Quando o usuário solicitar o download em lote  
Então o sistema não deve apresentar o lote como totalmente concluído sem informar a
falha, documentos que não estejam em `COMPLETED` não devem ser tratados como incluídos
com sucesso e o usuário deve ser informado sobre quais itens foram ignorados.

### AC-009 — caminho de erro da exclusão em lote

Dado que o usuário confirmou a exclusão de vários documentos  
Quando o backend retornar falha para a operação ou para parte dos documentos  
Então o sistema deve informar sucesso parcial usando uma mensagem padrão para cada
item não removido e não deve remover da listagem documentos cuja exclusão não tenha sido
confirmada pelo backend.

### AC-010 — regressão dos fluxos existentes

Dado que a nova consulta e as ações em lote estão disponíveis  
Quando o usuário registrar um documento, consultar um documento por identificador,
baixar um documento individual ou excluí-lo individualmente  
Então esses fluxos devem continuar funcionando conforme seus contratos atuais.

## 6. Casos de erro / edge cases

- Busca ou filtro sem resultados → exibir lista vazia e não exibir itens fora dos
  critérios.
- Página fora do intervalo válido → informar ou normalizar conforme o contrato da API;
  nunca retornar silenciosamente uma página incorreta.
- Tamanho de página inválido ou acima do limite aprovado → rejeitar ou aplicar a regra
  de limite documentada.
- Campo ou direção de ordenação inválidos → informar erro ou aplicar o padrão aprovado;
  não ordenar de forma ambígua.
- Alteração dos critérios durante uma requisição → a resposta antiga não deve substituir
  silenciosamente a consulta mais recente.
- Mudança de página, busca, filtro ou ordenação → preservar a seleção dos documentos já
  selecionados.
- Documento selecionado que deixou de existir antes da ação em lote → informar o
  conflito e não tratá-lo como sucesso integral.
- Documento selecionado sem conteúdo disponível → seguir o contrato aprovado para
  falha parcial do download em lote.
- Falha parcial na exclusão em lote → manter visíveis os documentos sem confirmação de
  exclusão e informar o resultado real.
- Lista vazia no carregamento inicial → preservar a mensagem já aprovada:
  “Nenhum documento encontrado”.
- Falha ao carregar ou atualizar a lista → preservar a mensagem já aprovada:
  “Não foi possível exibir itens listados”.
- Seleção sem itens → impedir a ação em lote e informar, usando mensagem padrão, que é
  necessário selecionar pelo menos um documento.

## 7. Fora de escopo desta feature

- Alterar o upload, o acompanhamento de estado ou a consulta de um documento por
  identificador.
- Alterar o download individual ou a exclusão individual além do necessário para
  compartilhar contratos e regras com as operações em lote.
- Preview ou visualização inline do conteúdo.
- Busca textual avançada, indexação externa, relevância ou OCR não descritos nesta
  feature.
- Autenticação, autorização, multi-tenant, CORS ou BFF.
- Acesso direto do navegador a MinIO, RabbitMQ ou PostgreSQL.
- Deploy ou hospedagem externa.

## 8. Suposições e perguntas abertas

- [x] Suposição: esta Spec revisa e amplia a feature existente
  `listagem-download-exclusao-documentos`, em vez de criar um novo domínio separado.
- [x] Informação confirmada pelo estado atual do código: já existe `GET /documents`,
  mas ele retorna a coleção completa sem busca, filtros, paginação ou ordenação.
- [x] Decisão: serão criados ou ampliados os parâmetros do `GET /documents` para
  suportar a nova consulta; não haverá endpoints GET separados por critério.
- [x] Decisão: busca, filtros, paginação e ordenação continuarão no endpoint
  `GET /documents`, usando query parameters. Não haverá um endpoint GET separado para
  cada critério. Os parâmetros oficiais são `search`, `status`, `contentType`,
  `createdFrom`, `createdTo`, `updatedFrom`, `updatedTo`, `page` e `sort`.
- [x] Decisão: a busca usa `search` como correspondência parcial (`contains`) em
  `originalFilename`, sem diferenciar maiúsculas de minúsculas. Não haverá normalização
  adicional de acentos nesta rodada; será usada a comparação padrão do PostgreSQL/JPA.
  Esta ausência de normalização é uma suposição técnica baseada no schema e nas
  dependências atuais, não uma regra de produto adicional.
- [x] Decisão: os filtros disponíveis são `status`, `contentType` e data.
  A data é filtrada tanto por `createdAt` quanto por `updatedAt`, com parâmetros
  `createdFrom`/`createdTo` e `updatedFrom`/`updatedTo`, usando instants ISO-8601 em UTC
  e intervalo semiaberto `[from,to)`, como convenção técnica compatível com os campos
  Java `Instant` existentes.
- [x] Decisão: os filtros preenchidos são combinados por AND; filtros vazios não
  participam da filtragem. Esta versão aceita uma condição por campo de filtro.
- [x] Decisão: a paginação usa dez itens por página e a interface exibe a página atual
  e o total no formato X/Y. `page` é 1-based e a resposta usa o envelope
  `content`, `page`, `size`, `totalElements` e `totalPages`, com `size: 10`; essa
  estrutura segue a convenção de paginação do ecossistema Spring sem expor diretamente
  o tipo `Page`.
- [x] Decisão: todos os campos exibidos na listagem podem ser usados para ordenação.
  A ordenação padrão é `updatedAt,desc`, com desempate por `createdAt,desc` e depois
  `id,asc`; o desempate é uma convenção técnica para garantir resultado determinístico.
- [x] Decisão: busca, filtros, paginação e ordenação são executados no backend; o
  frontend apenas envia os critérios e exibe a resposta.
- [x] Decisão: o download em lote entrega um único arquivo compactado contendo os
  documentos selecionados que estejam em `COMPLETED`. O arquivo usa o formato ZIP,
  nome `documents.zip`, `Content-Type: application/zip` e `Cache-Control: no-store`.
  Itens que não estejam em `COMPLETED` são ignorados e o frontend informa ao usuário
  quais não foram baixados.
- [x] Decisão: a exclusão em lote é enviada como uma única requisição contendo vários
  IDs por `DELETE /documents`, com corpo `{ "documentIds": [...] }`. A exclusão
  individual continua em `DELETE /documents/{documentId}`.
- [x] Decisão: a exclusão em lote permite sucesso parcial; cada item não removido deve
  retornar `200 OK` com `results` por item. Cada item não removido recebe status,
  `errorCode` e mensagem padrão de falha e permanece na listagem. Falha da requisição
  inteira usa `ProblemDetail` em `application/problem+json`.
- [x] Decisão: o download em lote será solicitado por `POST /documents/batch-download`,
  com corpo `{ "documentIds": [...] }`, porque a operação recebe uma coleção de IDs e
  retorna conteúdo binário. Se uma mudança de estado ocorrer durante o streaming, o
  backend deve omitir o item e devolver seus IDs no header
  `X-DocFlow-Skipped-Documents`; se nenhum item puder ser incluído, deve retornar `409`
  com `skippedDocumentIds` em `ProblemDetail`.
- [x] Decisão: documentos `PENDING` e `PROCESSING` podem ser excluídos em lote. A
  implementação deve tratar a corrida com staging, RabbitMQ, retries e consumidor
  conforme a política de exclusão definitiva aprovada.
- [x] Decisão: a seleção sobrevive à troca de página, busca, filtro e ordenação; não há
  limite máximo de documentos selecionados por operação.
- [x] Decisão: a confirmação explícita continua obrigatória para exclusão em lote,
  seguindo o comportamento já existente para exclusão individual.
- [x] Decisão: erros de consulta, download em lote, exclusão em lote e seleção vazia
  usam as mensagens padrão da aplicação. O catálogo exato dessas mensagens pode ser
  registrado no contrato de UX/API.
- [x] Restrição arquitetural: nenhuma decisão desta feature deve expor `objectKey`,
  credenciais ou acesso direto aos serviços de infraestrutura no frontend.
- [x] Detalhe técnico: durante a resposta ZIP em streaming, os IDs omitidos serão
  transportados no header `X-DocFlow-Skipped-Documents`, separados por vírgula. Se todos
  forem omitidos, a API retornará `409` com `skippedDocumentIds` em `ProblemDetail`.

## 9. Definition of Done desta feature

- [ ] As perguntas abertas da seção 8 que impactam contrato ou comportamento foram
  respondidas e aprovadas por Marcos.
- [ ] Os contratos GET de busca, filtros, paginação e ordenação foram documentados e
  testados.
- [ ] O contrato de download em lote foi definido, implementado e testado.
- [ ] O contrato de exclusão em lote e sua política para falhas parciais foram definidos,
  implementados e testados.
- [ ] Os critérios de aceite da seção 5 são satisfeitos.
- [ ] Casos de erro de prioridade alta tratados sem apresentar falha como sucesso.
- [ ] Upload, consulta por identificador, download individual e exclusão individual
  continuam funcionando sem regressão óbvia.
- [ ] Alterações de schema, caso necessárias, foram feitas por migration Flyway.
- [ ] O plano e as tarefas da feature foram atualizados para refletir esta Spec aprovada,
  respeitando a ordem incremental definida pelo projeto.
- [ ] A convergência `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes` foi revisada.
- [ ] Marcos revisou e aprovou a Spec e a implementação antes de qualquer deploy.
