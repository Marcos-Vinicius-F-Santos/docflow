# Spec da Feature — Frontend Angular para o fluxo de documentos

Status: Rascunho  
Projeto: DocFlow  
Onde vive no código: `frontend/`, consumindo a API REST do domínio `document`

## 1. Objetivo

Implementar uma interface Angular para enviar um documento ao DocFlow e acompanhar,
por identificador, o processamento assíncrono até `COMPLETED` ou `FAILED`. A interface
deve refletir fielmente o contrato HTTP e os estados já implementados no backend, sem
acessar diretamente PostgreSQL, RabbitMQ ou MinIO.

Nesta primeira versão, “todo o fluxo” significa registro do conteúdo e acompanhamento
do ciclo de vida exposto pelas APIs existentes. Listagem global, download/visualização
do binário, autenticação, autorização e multi-tenant dependem de contratos que ainda
não existem.

## 2. Como funciona hoje

- O frontend ainda não possui aplicação Angular funcional: não há rotas, componentes,
  serviços, testes ou dependências utilizáveis; os diretórios existentes são apenas a
  estrutura inicial do projeto.
- O backend expõe `POST /documents` e `GET /documents/{documentId}`.
- O registro exige `multipart/form-data` com uma única parte obrigatória chamada
  `file`; partes `metadata` ou outras partes devem ser rejeitadas.
- O limite funcional e HTTP é `52.428.800` bytes (50 MiB). O backend valida conteúdo
  vazio, filename, `Content-Type` e compatibilidade entre o tipo declarado e a assinatura
  detectada pelo Tika.
- Em sucesso, o `POST` responde `201 Created`, inclui `Location: /documents/{id}` e
  devolve os metadados, os timestamps e o estado inicial `PENDING`.
- O processamento assíncrono pode observar `PENDING`, `PROCESSING`, `COMPLETED` ou
  `FAILED`. `RESULT_UNKNOWN` é tratado internamente e continua exposto ao cliente como
  `PROCESSING` até a reconciliação concluir.
- O `GET` retorna os metadados e o estado do documento; não retorna o conteúdo binário,
  `objectKey`, contador de reconciliação ou motivo detalhado da falha.
- Um documento inexistente resulta em `404` sem corpo útil para a interface.
- Erros de validação do upload retornam `400 application/problem+json`, com `errorCode`
  e, quando aplicável, `invalidField`.
- Não foram encontrados mecanismos de autenticação, autorização, multi-tenant, CORS
  configurado, listagem, busca, download, preview, exclusão ou retry manual.

## 3. Requisitos funcionais

### FR-001

QUANDO o usuário acessar a aplicação
O SISTEMA DEVE exibir uma entrada para selecionar ou arrastar um único arquivo e iniciar
o registro do documento.

### FR-002

QUANDO o usuário selecionar um arquivo
O SISTEMA DEVE exibir nome, tipo informado pelo navegador e tamanho, permitindo revisar
o arquivo antes do envio.

### FR-003

QUANDO o arquivo estiver vazio, exceder `52.428.800` bytes, não possuir nome ou não
possuir tipo informado pelo navegador
O SISTEMA DEVE impedir o envio e informar o problema localmente, sem substituir a
validação autoritativa do backend.

### FR-004

QUANDO o usuário confirmar um arquivo válido localmente
O SISTEMA DEVE enviar `POST /documents` como `multipart/form-data`, usando somente a
parte `file`, sem enviar JSON de metadados nem credenciais de storage.

### FR-005

ENQUANTO o upload HTTP estiver em andamento
O SISTEMA DEVE indicar que o envio está em progresso e impedir submissões duplicadas da
mesma ação até receber uma resposta ou permitir uma nova tentativa.

### FR-006

QUANDO o backend responder `201 Created`
O SISTEMA DEVE exibir o identificador, nome, tipo, tamanho, data de criação e estado
`PENDING`, além de oferecer acesso à tela de acompanhamento do documento.

### FR-007

QUANDO a tela de acompanhamento possuir um `documentId`
O SISTEMA DEVE consultar `GET /documents/{documentId}` e atualizar os dados exibidos
com base na resposta do backend.

### FR-008

QUANDO o backend retornar `PENDING`, `PROCESSING`, `COMPLETED` ou `FAILED`
O SISTEMA DEVE apresentar o estado com texto e indicação visual distintos, sem tratar
`PENDING` ou `PROCESSING` como sucesso final.

### FR-009

ENQUANTO o estado observado for `PENDING` ou `PROCESSING`
O SISTEMA DEVE consultar automaticamente o estado a cada 3 segundos, oferecer também uma
ação manual de atualização e explicar que o processamento é assíncrono. O polling deve
parar ao receber `COMPLETED`, `FAILED` ou `404`.

### FR-010

QUANDO o estado observado for `COMPLETED`
O SISTEMA DEVE informar que o armazenamento final foi confirmado e exibir os metadados
conhecidos do documento.

### FR-011

QUANDO o estado observado for `FAILED`
O SISTEMA DEVE informar que o processamento falhou e oferecer uma nova tentativa de
consulta ou retorno ao fluxo de novo upload, sem prometer retry do processamento quando
essa operação não existir na API.

### FR-012

QUANDO o backend responder `400 application/problem+json`
O SISTEMA DEVE traduzir os códigos estáveis abaixo para mensagens compreensíveis,
mantendo uma mensagem genérica para códigos desconhecidos:

- `DOCUMENT_CONTENT_MISSING`
- `DOCUMENT_CONTENT_EMPTY`
- `DOCUMENT_FILENAME_MISSING`
- `DOCUMENT_CONTENT_TYPE_MISSING`
- `DOCUMENT_CONTENT_TYPE_MISMATCH`
- `DOCUMENT_CONTENT_TYPE_UNKNOWN`
- `DOCUMENT_CONTENT_TOO_LARGE`
- `DOCUMENT_METADATA_NOT_ALLOWED`
- `DOCUMENT_CONTENT_READ_FAILED`

### FR-013

QUANDO o backend responder `404` para uma consulta
O SISTEMA DEVE informar que o documento não foi encontrado e não exibir dados locais
como se fossem o estado atual do backend.

### FR-014

QUANDO ocorrer falha de rede, timeout, resposta `5xx` ou erro não reconhecido
O SISTEMA DEVE exibir uma falha operacional genérica, preservar o identificador local
quando já existir e permitir repetir a consulta ou o envio conforme o ponto em que a
falha ocorreu.

### FR-015

QUANDO o usuário abrir diretamente uma rota de acompanhamento contendo um identificador
válido
O SISTEMA DEVE carregar o documento pelo `GET /documents/{documentId}`, sem depender
exclusivamente do estado mantido em memória após o upload.

### FR-016

QUANDO a aplicação precisar acessar o backend
O SISTEMA DEVE usar uma URL base configurável por ambiente ou proxy de desenvolvimento,
sem hardcode de credenciais, tokens ou endpoints de MinIO/RabbitMQ.

## 4. Regras de negócio

### BR-001

O frontend deve enviar exatamente uma parte multipart chamada `file`; o backend é a
fonte de verdade para tipo detectado, tamanho persistido e validade do conteúdo.

### BR-002

`PENDING` significa que o recebimento foi aceito após staging e publicação confirmada;
não significa que o binário final já foi armazenado.

### BR-003

`PROCESSING` significa que o processamento final ainda não foi confirmado. Esse estado
pode permanecer por tempo prolongado quando o resultado do storage for inconclusivo;
isso não deve ser exibido como erro imediato nem como sucesso.

### BR-004

Somente `COMPLETED` representa armazenamento final confirmado para a experiência do
usuário.

### BR-005

O frontend não pode acessar diretamente buckets, filas, banco ou SDKs de infraestrutura.

### BR-006

Como não há endpoint de listagem, a interface não deve afirmar que conhece todos os
documentos existentes no DocFlow.

## 5. Critério de aceite

### AC-001 — caminho feliz

Dado que o backend está disponível e o usuário selecionou um arquivo não vazio de até
50 MiB com nome e tipo informados  
Quando o usuário confirmar o envio  
Então o frontend envia somente a parte `file` para `POST /documents`, recebe `201 Created`,
exibe os metadados e `PENDING`, consulta o recurso retornado e atualiza a tela até exibir
`COMPLETED` quando o backend confirmar o armazenamento final.

### AC-002 — caminho de erro de validação

Dado que o usuário selecionou um arquivo cujo tipo declarado não corresponde à assinatura
detectada pelo backend  
Quando o usuário confirmar o envio  
Então o frontend recebe `400 application/problem+json`, identifica
`DOCUMENT_CONTENT_TYPE_MISMATCH`, mostra uma mensagem associada ao arquivo e não exibe
o upload como aceito.

### AC-003 — caminho de erro de processamento

Dado que o upload foi aceito e o backend retornou `PENDING`  
Quando o processamento assíncrono terminar em `FAILED`  
Então o frontend exibe `FAILED`, informa que o armazenamento final não foi concluído e
oferece nova consulta ou novo upload, sem exibir `COMPLETED`.

### AC-004 — caminho de erro de consulta

Dado que a rota contém um identificador inexistente  
Quando o frontend consultar `GET /documents/{documentId}`  
Então o backend responderá `404` e o frontend exibirá “documento não encontrado”, sem
tratar a ausência como falha de upload ou como documento `PENDING`.

## 6. Casos de erro / edge cases

- Arquivo acima de 50 MiB → bloquear localmente e também tratar
  `DOCUMENT_CONTENT_TOO_LARGE` caso o backend seja quem detectar o limite.
- Arquivo vazio → bloquear localmente e tratar `DOCUMENT_CONTENT_EMPTY`.
- Tipo incompatível ou assinatura desconhecida → exibir o `errorCode` retornado; não
  tentar corrigir o `Content-Type` silenciosamente.
- O usuário recarrega a página durante `PENDING` ou `PROCESSING` → permitir reabrir a
  rota por `documentId` e consultar o backend novamente.
- A resposta do `POST` é perdida depois que o servidor pode ter aceitado o documento →
  exibir erro operacional sem repetir automaticamente um upload que pode criar um
  segundo documento.
- `PROCESSING` permanece por muito tempo → informar que a reconciliação é interna e
  permitir consulta manual; não declarar falha somente por timeout do frontend.
- Duas abas consultam o mesmo documento → ambas devem tratar o backend como fonte de
  verdade e não sobrescrever o estado com dados locais antigos.
- A resposta do backend não traz motivo detalhado para `FAILED` → usar mensagem
  genérica e registrar essa limitação como pergunta aberta, sem inventar causa.

## 7. Fora de escopo desta feature

- Implementar autenticação, autorização ou multi-tenant.
- Criar endpoint de listagem, busca, filtros, paginação ou ordenação.
- Criar endpoint de download, preview ou streaming do conteúdo final.
- Criar exclusão, cancelamento, retry manual ou reprocessamento.
- Alterar o backend para expor motivo de falha, progresso percentual, tentativas de
  reconciliação ou eventos em tempo real.
- Acessar diretamente MinIO, RabbitMQ ou PostgreSQL pelo navegador.
- Deploy ou hospedagem externa.

## 8. Suposições, decisões e perguntas abertas

- [x] Suposição: nesta primeira feature, “todo o fluxo” significa upload e acompanhamento
  do ciclo de vida já exposto por `POST`/`GET`; listagem e download serão features
  separadas porque não há APIs correspondentes.
- [x] Decisão: o acompanhamento usará polling automático a cada 3 segundos e atualização
  manual. O polling será encerrado em `COMPLETED`, `FAILED` ou `404`.
- [x] Decisão: a implementação usará a versão estável mais recente do Angular disponível
  no início do desenvolvimento, com componentes standalone e Angular Material como
  biblioteca visual oficial. O número exato da versão será fixado no `package.json` no
  momento da implementação.
- [x] Suposição operacional: durante o desenvolvimento, a API será acessada por proxy
  local; em produção, frontend e backend deverão preferencialmente compartilhar a mesma
  origem. CORS só será necessário se forem hospedados em origens diferentes.
- [x] Decisão: não haverá lista local de documentos recentes na primeira versão, pois não
  existe endpoint de listagem e essa lista não representaria necessariamente o backend.
- [x] Decisão: copiar ID/link não é necessário para o fluxo principal e não será requisito
  desta primeira versão.
- [x] Decisão: não haverá whitelist adicional de tipos no frontend. A validação por
  assinatura do backend continua sendo autoritativa.
- [x] Decisão: uma falha de infraestrutura no `POST` exibirá erro operacional e permitirá
  nova tentativa manual, sem reenvio automático. Se nenhum `documentId` estiver disponível,
  não haverá consulta automática do documento.

## 9. Definition of Done desta feature

- [ ] Critérios de aceite da seção 5 satisfeitos
- [ ] Upload multipart com a parte `file` validado contra o backend real
- [ ] Estados `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED` apresentados corretamente
- [ ] Códigos de erro `400` tratados sem acoplamento a mensagens frágeis
- [ ] Consulta direta por `documentId` funciona após recarregar a página
- [ ] Falhas de rede, `404` e `5xx` possuem tratamento compreensível
- [ ] Nenhum segredo ou credencial de infraestrutura chega ao navegador
- [ ] Regressão do backend checada; a suíte atual passou com 92 testes
- [ ] Testado seguindo `specs/05-verificacao/plano-de-teste-template.md`
- [ ] Eu revisei e aprovei a Spec e a implementação antes do deploy
