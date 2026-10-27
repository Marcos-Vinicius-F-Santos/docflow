# Spec da Feature — Hub de documentos

Status: Aprovada  
Projeto: DocFlow  
Onde vive no código: `frontend/src/app/`, consumindo os contratos REST existentes do domínio `document`

## 1. Objetivo

Criar um hub simples para o DocFlow e torná-lo a tela inicial da aplicação. O hub
deve permitir que o usuário, sem autenticação ou vínculo a uma conta nesta fase, veja
os documentos já enviados, a fila de documentos pendentes, os últimos cinco envios,
as falhas e as ações de download disponíveis.

O hub deve concentrar o dropbox para um novo arquivo e a visão geral dos documentos,
mantendo o acompanhamento por identificador a partir dos documentos exibidos no próprio
Hub, com download e exclusão na página de acompanhamento. A listagem deve usar blocos compactos,
com o nome truncado e navegar para o acompanhamento ao clicar.

Referência visual: wireframe fornecido pelo usuário. A imagem orienta a composição
geral — header, faixa lateral, dropbox e área central — mas não define dimensões,
tipografia ou estilos finais.

## 2. Como funciona hoje

- A rota `/` exibe o Hub como tela inicial.
- O Hub concentra o upload de um arquivo por seleção ou arraste, valida o arquivo
  localmente e envia `POST /documents` como `multipart/form-data`.
- O Hub lista todos os documentos retornados por `GET /documents`; cada documento
  oferece acesso ao acompanhamento em `/documents/:documentId`.
- As rotas `/documents` e `/documents/new` não fazem mais parte da navegação do
  produto. O acompanhamento por identificador continua disponível a partir de um
  documento listado no Hub.
- A listagem atual permite download individual de documentos `COMPLETED` e exclusão
  individual com confirmação explícita.
- A rota `/documents/:documentId` continua disponível para acompanhamento; não há
  tela separada de listagem ou upload.
- O backend não possui autenticação, vínculo de documento a usuário nem endpoint
  específico para tentar novamente um documento `FAILED`.

## 3. Requisitos funcionais

### FR-001

QUANDO o usuário acessar a rota `/`  
O SISTEMA DEVE exibir o Hub como tela inicial, sem redirecionar diretamente para
`/documents/new`.

### FR-002

QUANDO o Hub for exibido  
O SISTEMA DEVE apresentar um header com o nome do aplicativo DocFlow.

### FR-003

QUANDO o Hub for exibido  
O SISTEMA DEVE apresentar uma coluna lateral com duas listas: “Atividade”, contendo no
máximo os cinco documentos mais recentes, e “Atenção”, contendo no máximo os cinco
documentos `FAILED` mais recentes. O Hub não deve possuir uma seção própria de downloads.

### FR-004

QUANDO o usuário visualizar a área principal do Hub  
O SISTEMA DEVE apresentar um dropbox que aceite arrastar um arquivo ou selecionar um
arquivo para iniciar um novo envio.

### FR-005

QUANDO o usuário enviar um arquivo pelo dropbox do Hub  
O SISTEMA DEVE reutilizar o fluxo de upload multipart existente, sem enviar credenciais
ou acessar diretamente PostgreSQL, RabbitMQ ou MinIO pelo navegador. Não deve existir
uma tela ou opção de upload separada em `/documents/new`.

### FR-006

QUANDO o Hub carregar os documentos já enviados  
O SISTEMA DEVE exibir todos os documentos retornados por `GET /documents`, incluindo
documentos nos estados `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.

### FR-007

QUANDO um documento estiver em `COMPLETED`  
O SISTEMA DEVE indicar visualmente o documento com um check verde e disponibilizar a
ação de download conforme o contrato existente.

### FR-008

QUANDO um documento estiver em `PENDING` ou `PROCESSING`  
O SISTEMA DEVE indicar visualmente o documento com um refresh azul e deixá-lo
identificado como pendente ou em processamento, sem apresentá-lo como concluído.

### FR-009

QUANDO um documento estiver em `FAILED`  
O SISTEMA DEVE indicar visualmente o documento com um X vermelho e apresentar uma
opção visível de tentar novamente. A tentativa deve reprocessar o staging existente,
enquanto o contador persistido `falhas` for menor que três; a terceira falha encerra
essa possibilidade e inicia a marcação para exclusão.

### FR-010

QUANDO uma tentativa de processamento falhar  
O SISTEMA DEVE incrementar o atributo persistido `falhas` em um e manter o documento
com o status `FAILED`.

### FR-011

QUANDO uma tentativa de processamento terminar com sucesso  
O SISTEMA DEVE manter o documento com o status `COMPLETED`, definir `falhas` como zero
e manter `isExcluded` como falso.

### FR-012

QUANDO o usuário solicitar retry para um documento `FAILED` cujo contador `falhas` seja
menor que três  
O SISTEMA DEVE reprocessar o staging existente e não deve criar automaticamente um
novo arquivo ou staging.

### FR-013

QUANDO o atributo `falhas` atingir três  
O SISTEMA DEVE definir `isExcluded` como verdadeiro, impedir novos retries pelo Hub e
definir `exclusionScheduledAt` para o prazo de três horas, encaminhando o documento
para a fila de exclusão. Ao fim do prazo, a exclusão deve remover o conteúdo binário,
o staging e os metadados persistidos.

### FR-014

QUANDO um documento estiver marcado com `isExcluded`  
O SISTEMA DEVE permitir que o usuário solicite sua exclusão imediata, sem aguardar o
prazo de três horas da fila de exclusão.

### FR-015

QUANDO o usuário solicitar retry pelo Hub  
O SISTEMA DEVE chamar `POST /documents/{documentId}/retry` e tratar `202 Accepted` como
aceitação do reprocessamento assíncrono, mantendo o documento observável pelo Hub.

### FR-016

QUANDO o usuário selecionar um documento no Hub  
O SISTEMA DEVE navegar para `/documents/:documentId`, sem exigir uma listagem ou tela de
upload separada.

### FR-017

QUANDO a aplicação estiver sendo usada nesta fase sem login  
O SISTEMA DEVE tratar os documentos retornados pela API como pertencentes ao contexto
único da instalação, sem exigir usuário autenticado ou associar documentos a um
usuário.

### FR-018

QUANDO o Hub exibir um documento na lista central  
O SISTEMA DEVE apresentar o documento em um bloco compacto, mostrar o início do nome com
reticências quando necessário e fornecer um tooltip com o nome completo no hover.

QUANDO o usuário clicar no bloco de um documento  
O SISTEMA DEVE navegar para `/documents/:documentId` para que o acompanhamento seja
exibido em uma página própria.

### FR-019

QUANDO o usuário estiver na página de acompanhamento de um documento `COMPLETED`  
O SISTEMA DEVE disponibilizar download e exclusão, e exibir ao final da página um preview
somente leitura quando o conteúdo for um PDF.

## 4. Regras de negócio

### BR-001

O Hub não terá autenticação nem associação entre documento e usuário nesta versão.

### BR-002

`COMPLETED` representa conteúdo final confirmado e pode exibir download.

### BR-003

`PENDING` e `PROCESSING` representam documentos ainda não concluídos e devem usar a
indicação visual de pendência definida para o Hub.

### BR-004

`FAILED` representa falha de processamento e nunca deve ser exibido com o check verde
de conclusão.

### BR-005

O Hub deve usar a API REST como fonte de verdade e não pode ler diretamente banco,
filas, buckets ou SDKs de infraestrutura.

### BR-006

Uma ação de tentar novamente deve ser explícita do usuário. O Hub não deve disparar
retry automático somente por detectar o estado `FAILED`. Cada ação deve solicitar o
reprocessamento do staging existente e o documento pode ter no máximo três falhas de
processamento no ciclo, contando a falha inicial.

### BR-007

O dropbox do Hub deve respeitar as validações e os limites já existentes do fluxo de
upload.

### BR-008

Os últimos cinco documentos enviados devem ser ordenados por `updatedAt` decrescente.
Quando `updatedAt` não estiver disponível, o Hub deve usar `createdAt` decrescente.

### BR-009

A coluna lateral deve limitar “Atividade” aos cinco documentos mais recentes e
“Atenção” aos cinco documentos `FAILED` mais recentes, usando `updatedAt` decrescente
com fallback para `createdAt`.

### BR-010

Após um upload bem-sucedido iniciado no Hub, o usuário deve permanecer no Hub e a lista
de documentos deve ser atualizada com o documento recebido pelo backend.

### BR-011

Quando a terceira tentativa de retry do mesmo documento falhar, o documento não deve
receber novas tentativas pelo Hub. O arquivo/staging associado deve ser marcado para
exclusão e o usuário deve enviar um novo arquivo para iniciar outro processamento.

### BR-012

Todo documento deve possuir o atributo inteiro `falhas`, iniciado em zero. Uma falha
de processamento incrementa esse atributo; uma conclusão bem-sucedida define seu valor
como zero.

### BR-013

Todo documento deve possuir o atributo booleano `isExcluded`, iniciado como falso. Ao
atingir três falhas — contando a falha do processamento inicial — o atributo deve ser
definido como verdadeiro, `exclusionScheduledAt` deve registrar o prazo de três horas
e o documento deve entrar na fila de exclusão. Ao fim do prazo, conteúdo binário,
staging e metadados devem ser removidos.

### BR-014

A exclusão manual solicitada pelo usuário pode ocorrer antes do prazo da fila e deve
seguir a política de exclusão definitiva aprovada para o documento.

### BR-015

O contador `falhas` representa as falhas do ciclo de processamento do documento; não é
um histórico ilimitado de eventos. Depois de uma conclusão bem-sucedida, o valor
persistido volta a zero.

### BR-016

O retry deve usar lock do documento e só pode ser aceito quando o status for `FAILED`,
`falhas < 3`, `isExcluded = false` e o staging ainda existir. Uma solicitação repetida
enquanto o documento estiver `PROCESSING` não deve criar uma segunda mensagem de
processamento.

### BR-017

O endpoint de retry retorna `404` para documento inexistente, `409` para documento não
elegível, `503` para staging/storage indisponível e `202 Accepted` quando o retry for
publicado para processamento assíncrono.

### BR-018

A fila de exclusão deve ser durável, usar TTL de três horas e processar a remoção de
forma idempotente. Se o usuário excluir o documento antes do vencimento, uma mensagem
posterior para o mesmo documento deve ser confirmada sem recriar ou falhar o documento.

### BR-019

Os documentos da área central devem ser apresentados em blocos compactos. O nome exibido
deve ser truncado visualmente quando necessário, mantendo o nome completo em tooltip. O
clique no bloco deve navegar para `/documents/:documentId`, onde ficam os detalhes e as
ações disponíveis.

### BR-020

A página de acompanhamento deve oferecer as ações de download e exclusão do documento.
Para documentos `COMPLETED` cujo tipo seja `application/pdf`, deve exibir um preview
somente leitura ao final da página, consumindo conteúdo servido inline pelo backend.

## 5. Critério de aceite

### AC-001 — caminho feliz do Hub

Dado que a aplicação está disponível e existem documentos retornados por `GET /documents`  
Quando o usuário acessar `/`  
Então o sistema deve exibir o Hub com o nome DocFlow no header, a coluna lateral com
“Atividade” e “Atenção”, o dropbox e a área central com todos os documentos disponíveis.
“Atividade” e “Atenção” devem conter no máximo cinco itens cada, pela regra de ordenação
definida, sendo “Atenção” limitada aos `FAILED` mais recentes.

### AC-002 — caminho feliz do upload pelo Hub

Dado que o usuário está no Hub e selecionou um arquivo válido  
Quando ele arrastar o arquivo para o dropbox ou confirmar a seleção  
Então o sistema deve iniciar o fluxo multipart existente, indicar o progresso e não
enviar metadados ou credenciais de infraestrutura ao backend, permanecer no Hub após o
sucesso e atualizar a lista com o novo documento.

### AC-003 — indicação dos estados

Dado que a lista contém documentos `COMPLETED`, `PENDING`, `PROCESSING` e `FAILED`  
Quando o Hub renderizar a área de documentos  
Então os concluídos devem usar check verde, os pendentes/em processamento devem usar
refresh azul e os falhos devem usar X vermelho, sem confundir pendência com sucesso.

### AC-004 — ação para documento falho

Dado que existe um documento no estado `FAILED` e ainda há menos de três retries  
Quando o usuário clicar em tentar novamente  
Então o sistema deve chamar `POST /documents/{documentId}/retry`, receber `202 Accepted`
e solicitar o reprocessamento assíncrono do staging existente, sem criar um novo arquivo.

### AC-005 — limite de retry

Dado que um documento `FAILED` possui `falhas` igual a um ou dois e `isExcluded` falso  
Quando o usuário solicitar um retry e o reprocessamento terminar com sucesso  
Então o documento deve ficar `COMPLETED`, `falhas` deve voltar para zero, `isExcluded`
deve permanecer falso e o Hub deve atualizar sua exibição.

### AC-006 — limite de três falhas

Dado que o mesmo documento falhou pela terceira vez  
Quando a terceira falha for persistida  
Então `falhas` deve ficar igual a três, `isExcluded` deve ficar verdadeiro,
`exclusionScheduledAt` deve registrar o prazo, o documento deve entrar na fila de
exclusão por três horas e o Hub não deve oferecer uma
quarta tentativa. Ao fim do prazo, o conteúdo, o staging e os metadados do documento
devem ser removidos.

### AC-007 — exclusão manual antes do prazo

Dado que um documento está marcado para exclusão e ainda não completou três horas na
fila  
Quando o usuário solicitar sua exclusão direta  
Então o sistema deve iniciar a exclusão imediatamente, sem aguardar o prazo da fila.

### AC-008 — erro ao carregar documentos

Dado que `GET /documents` falha ou está indisponível  
Quando o usuário acessar o Hub  
Então o sistema deve exibir um estado de erro compreensível, não apresentar uma lista
desatualizada como se fosse atual e oferecer uma forma de tentar novamente.

### AC-009 — acompanhamento a partir do Hub

Dado que o Hub está configurado como tela inicial e existe um documento listado  
Quando o usuário selecionar esse documento  
Então o sistema deve navegar para `/documents/:documentId` e exibir seu acompanhamento.

### AC-010 — blocos compactos e navegação

Dado que o Hub exibe um documento em um bloco compacto  
Quando o usuário clicar nesse bloco  
Então o sistema deve navegar para `/documents/:documentId`, manter o nome completo no
tooltip do bloco e exibir os metadados e status na página de acompanhamento.

### AC-011 — ações e preview no acompanhamento

Dado que existe um documento `COMPLETED` com tipo `application/pdf`  
Quando o usuário abrir `/documents/:documentId`  
Então o sistema deve exibir as opções de baixar e excluir, além de um preview somente
leitura do PDF ao final da página.

## 6. Casos de erro / edge cases

- A lista não contém documentos → exibir estado vazio sem inventar itens; o dropbox
  continua disponível.
- O upload pelo dropbox falha na validação → reutilizar as mensagens e o comportamento
  de validação já aprovados para o upload.
- O upload é aceito, mas a resposta não chega → exibir falha operacional sem repetir
  automaticamente um envio que pode ter sido aceito pelo backend.
- Um documento muda de estado enquanto o Hub está aberto → a atualização posterior da
  API deve prevalecer sobre a indicação local anterior.
- O staging necessário para o retry não está disponível → informar a falha, não simular
  sucesso e orientar o usuário a enviar outro arquivo quando não houver mais tentativa
  válida.
- O Hub não possui seção própria de downloads → a ação de download continua disponível
  somente junto aos documentos `COMPLETED`, conforme o contrato existente.
- A resposta de `GET /documents` contém um estado desconhecido → usar indicação
  genérica de estado não reconhecido e não tratá-lo como `COMPLETED`.

## 7. Fora de escopo desta feature

- Login, autenticação, autorização, multi-tenant ou associação de documentos a usuário.
- Criar histórico de usuários, contas ou permissões.
- Busca textual, filtros, paginação, ordenação avançada e operações em lote.
- Preview ou visualização inline de conteúdos que não sejam PDF.
- Retry automático ou reprocessamento silencioso de documentos.
- Alterar a política de retries RabbitMQ ou a reconciliação interna do backend.
- Acessar diretamente PostgreSQL, RabbitMQ, MinIO ou seus SDKs pelo frontend.
- Deploy ou hospedagem externa.

## 8. Suposições e perguntas abertas

- [x] Decisão confirmada: o Hub é a tela inicial e concentra upload e listagem.
  As rotas `/documents` e `/documents/new` não fazem parte do produto; somente
  `/documents/:documentId` permanece como tela de acompanhamento acessada pelo Hub.
- [x] Suposição: sem login, “todos os documentos” significa todos os documentos
  retornados pelo `GET /documents` da instalação atual.
- [x] Suposição: `PENDING` e `PROCESSING` compartilham a indicação visual de refresh
  azul, embora continuem sendo estados distintos no backend.
- [x] Decisão: os últimos cinco documentos enviados serão ordenados por `updatedAt`
  decrescente; quando `updatedAt` não estiver disponível, será usado `createdAt`
  decrescente.
- [x] Decisão adicional: “Atividade” mostra no máximo os últimos cinco documentos e
  “Atenção” mostra no máximo os cinco documentos `FAILED` mais recentes, usando a mesma
  ordenação por `updatedAt` e fallback para `createdAt`.
- [x] Decisão: o Hub não terá uma seção própria de downloads. Documentos `COMPLETED`
  continuarão oferecendo download junto à ação do documento.
- [x] Decisão: “tentar novamente” reprocessará o staging existente, somente mediante
  ação explícita do usuário e no máximo três vezes para o mesmo documento. Depois da
  terceira falha, o arquivo/staging será marcado para exclusão e o usuário deverá
  enviar outro arquivo/staging.
- [x] Decisão: o schema atual do documento receberá o contador inteiro `falhas`, iniciado
  em zero, e o booleano `isExcluded`, iniciado em falso. A mudança deverá ser feita por
  migration Flyway e os valores iniciais dos documentos existentes deverão ser definidos
  nessa migration.
- [x] Decisão aprovada: o Hub usará `POST /documents/{documentId}/retry`. O backend
  deverá adquirir lock, aceitar somente `FAILED` com `falhas < 3`, `isExcluded = false`
  e staging existente, publicar o reprocessamento e responder `202 Accepted`.
- [x] Decisão aprovada: `404` representa documento inexistente, `409` documento não
  elegível, `503` staging/storage indisponível e `202` retry aceito.
- [x] Decisão aprovada: o schema também terá `exclusionScheduledAt`, preenchido quando
  `falhas` chegar a três, para persistir o prazo de exclusão e permitir auditoria e
  recuperação operacional. O atributo também será criado por migration Flyway.
- [x] Decisão aprovada: a fila de exclusão será durável, usará TTL de três horas e seu
  consumidor removerá conteúdo binário, staging e metadados de forma idempotente.
- [x] Decisão: a falha do processamento inicial conta como a primeira das três falhas.
  Quando `falhas` chegar a três, não haverá uma quarta tentativa pelo Hub.
- [x] Decisão: a exclusão automática após três horas removerá o conteúdo binário, o
  staging e o registro de metadados do documento. A exclusão manual seguirá a política
  definitiva já aprovada e poderá ocorrer antes do prazo.
- [x] Decisão: depois de um upload bem-sucedido pelo Hub, o usuário permanecerá no Hub
  e a lista será atualizada com o documento retornado pelo backend.
- [x] Restrição confirmada pela referência visual: o wireframe define a composição
  geral, mas não fixa dimensões, estilos, conteúdo exato dos indicadores ou
  responsividade.

## 9. Definition of Done desta feature

- [ ] A Spec foi revisada e aprovada por Marcos.
- [ ] A rota `/` exibe o Hub sem redirecionar para uma tela de upload separada.
- [ ] Header, faixa lateral, dropbox e lista central estão implementados conforme o
  contrato aprovado.
- [ ] Os quatro estados públicos possuem indicação visual correta.
- [ ] O fluxo de upload existente funciona a partir do dropbox do Hub.
- [ ] A listagem ocorre diretamente no Hub; o acompanhamento, o download individual e a
  exclusão individual continuam funcionando a partir do documento selecionado.
- [ ] Os documentos aparecem em blocos compactos com tooltip do nome completo e navegação
  para uma página de acompanhamento com ações e preview PDF.
- [ ] A coluna lateral mostra até cinco documentos em “Atividade” e até cinco documentos
  `FAILED` recentes em “Atenção”, sem seção própria de downloads.
- [ ] O endpoint `POST /documents/{documentId}/retry` e seus status de resposta foram
  implementados e testados.
- [ ] A migration Flyway adiciona `falhas`, `isExcluded` e `exclusionScheduledAt` com
  valores iniciais seguros para os documentos existentes.
- [ ] A fila de exclusão aguarda três horas e executa o comportamento aprovado para o
  documento marcado, removendo conteúdo binário, staging e metadados.
- [ ] O limite de três falhas e a orientação para novo upload após a terceira falha
  foram testados.
- [ ] Os casos de erro de carregamento e upload foram testados.
- [ ] A convergência `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes` foi revisada.
- [ ] Marcos aprovou a implementação antes de qualquer deploy.
