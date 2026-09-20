# Tarefas Corretivas — Recebimento de conteúdo binário no registro de documentos

Feature: `entrada-binaria-documentos`  
Status: Proposta — aguardando aprovação de Marcos  
Data: 2026-09-16  
Plano relacionado: `specs/04-plano/entrada-binaria-documentos/plano.md`

## Motivo desta rodada

Esta rodada corrige a divergência encontrada na revisão de convergência:

- o `HEAD` versionado ainda recebe `POST /documents` como `@RequestBody` JSON;
- `MinioDocumentStorage` possui apenas `store`, `exists` e `delete`;
- não existe operação provider-neutral para ler o staging;
- as implementações multipart/staging encontradas no worktree não estão todas
  rastreadas nem podem ser tratadas como código disponível para deploy.

O template literal `specs/04-plano/FEATURE_TEMPLATE/tarefas.md` não existe neste
repositório. Este arquivo segue o formato das tarefas existentes.

## Regra de execução

As tarefas devem ser executadas em ordem. Nenhuma tarefa desta rodada está concluída;
cada uma só pode ser marcada depois da verificação descrita e da revisão de Marcos.

## Fase 0 — Rebaseline

- [ ] **C-001 [FR-001..FR-013]** Separar e identificar a revisão que será corrigida.
  - Arquivos/módulos: worktree inteiro, `git diff`, `git status` e histórico Git.
  - Verificação: existe uma lista explícita de arquivos aprovados; alterações não
    relacionadas são isoladas; a revisão final pode ser reproduzida a partir de um
    commit limpo.

- [ ] **C-002 [FR-001..FR-013]** Reconciliar o status das tarefas históricas com o código
  real, sem apagar o histórico da rodada anterior.
  - Arquivos/módulos: `tarefas.md`, checklist de convergência e esta rodada corretiva.
  - Verificação: nenhuma tarefa anterior é considerada evidência de implementação se o
    respectivo código/teste não estiver presente no commit promovido.

## Fase 1 — Base

- [ ] **C-003 [FR-006][FR-009][FR-010][FR-013]** Definir o contrato provider-neutral de
  staging para escrita e leitura.
  - Arquivos/módulos: `document/port/out/storage/` ou limite equivalente já existente.
  - Verificação: o contrato define gravação, abertura/leitura por `contentReference`,
    existência, remoção, metadados retornados e responsabilidade pelo fechamento do
    `InputStream`, sem importar Spring ou MinIO.

- [ ] **C-004 [FR-009][FR-010]** Definir a configuração executável do cliente MinIO e do
  bucket separado `docflow-staging`.
  - Arquivos/módulos: configuração Spring do domínio `document`,
    `application.properties` e `infra/.env.example`.
  - Verificação: o contexto cria o cliente usando endpoint e credenciais externas; o
    bucket não é público; nenhum segredo aparece no código ou nos testes versionados.

- [ ] **C-005 [FR-002][FR-004][FR-005][FR-011]** Consolidar as dependências e o contrato
  de leitura de stream para Tika e contagem de bytes.
  - Arquivos/módulos: `backend/pom.xml` e componentes de aplicação do domínio
    `document`.
  - Verificação: Maven resolve as dependências; o fluxo não usa `byte[]` nem arquivo
    temporário como representação interna; streams sem `mark/reset` são suportados.

## Fase 2 — Lógica principal

- [ ] **C-006 [FR-002][FR-003][FR-004]** Criar ou consolidar `DocumentContent` e o DTO
  próprio da entrada multipart.
  - Arquivos/módulos: `document/application/` e DTO da camada de entrada REST.
  - Verificação: o contrato contém `InputStream`, filename e content type; o
    Application Service não recebe `MultipartFile` nem depende de Spring Web.

- [ ] **C-007 [FR-005][FR-008][FR-011][FR-012]** Implementar a validação do conteúdo
  usando Tika e `CountingInputStream`.
  - Arquivos/módulos: validador de conteúdo e exceções do domínio `document`.
  - Verificação: testes cobrem arquivo ausente, content type vazio, conteúdo vazio,
    assinatura compatível, divergente, desconhecida, limite exato e conteúdo acima de
    `52428800` bytes; erros retornam códigos estáveis.

- [ ] **C-008 [FR-006][FR-009][FR-010][FR-011]** Implementar o adaptador MinIO de staging
  com escrita e leitura.
  - Arquivos/módulos: adaptador dentro do domínio `document`, usando o cliente MinIO
    configurado; não alterar o significado do `MinioDocumentStorage` final sem decisão.
  - Verificação: teste de integração grava e lê o mesmo conteúdo via
    `staging/{documentId}`, confirma tamanho/content type, bucket separado e fechamento
    do stream; falhas do MinIO são traduzidas sem sucesso falso.

- [ ] **C-009 [FR-006][FR-009][FR-010][FR-013]** Atualizar o Application Service para
  coordenar documento, staging e publicação na ordem aprovada.
  - Arquivos/módulos: `DocumentService` ou `document/application/`, repositório e
    portas de staging/publicação.
  - Verificação: teste unitário confirma `persistência → staging → publicação → resposta`,
    `sizeBytes` calculado do conteúdo e ausência de publicação quando validação ou
    staging falhar.

## Fase 3 — Interface

- [ ] **C-010 [FR-001][FR-002][FR-003][FR-007]** Alterar `POST /documents` para aceitar
  `multipart/form-data` com a parte obrigatória `file`.
  - Arquivos/módulos: `DocumentController` e DTO multipart.
  - Verificação: teste MVC confirma tradução para `DocumentContent`, filename e content
    type derivados da parte, e HTTP `400` para arquivo ausente e JSON antigo.

- [ ] **C-011 [FR-005][FR-008][FR-012]** Integrar o mapeamento de erros para
  `application/problem+json` e validar os limites HTTP preventivos.
  - Arquivos/módulos: `GlobalExceptionHandler` e `application.properties`.
  - Verificação: respostas inválidas têm `ProblemDetail`, `errorCode`,
    `invalidField` quando aplicável, e o limite configurado é exatamente:
    `52428800B` para arquivo e requisição.

- [ ] **C-012 [FR-006][FR-010][FR-013]** Conectar a porta de publicação ao contrato da
  feature de mensageria.
  - Arquivos/módulos: `document/application/` e contrato `DocumentStorageRequested`.
  - Verificação: o payload contém apenas `documentId`, `contentReference`, `sizeBytes`
    e `contentType`; não contém `InputStream` ou binário; publicação ocorre somente
    depois do staging.

  **Gate obrigatório:** como RabbitMQ foi adiado para uma feature posterior, esta tarefa
  não pode ser considerada concluída com um publisher fictício. Sem publisher, topologia
  e confirmação reais, a feature não está pronta para deploy conforme `FR-006`, `FR-010`
  e `FR-013`.

## Fase 4 — Testes

- [ ] **C-013 [FR-001][FR-002][FR-003][FR-004][FR-007]** Testar o contrato multipart e
  a fronteira Spring → aplicação.
  - Verificação: caminho feliz, arquivo ausente, JSON antigo, filename/content type e
    ausência de `MultipartFile` além da camada de entrada.

- [ ] **C-014 [FR-005][FR-008][FR-011][FR-012]** Testar validação, Tika, contagem e
  respostas de erro.
  - Verificação: todos os cenários de conteúdo inválido retornam `400` com o formato
    ProblemDetail esperado; contagem e fechamento do stream são confirmados.

- [ ] **C-015 [FR-006][FR-009][FR-010]** Testar escrita e leitura reais do staging MinIO.
  - Verificação: o conteúdo recuperado por `contentReference` é byte a byte igual ao
    conteúdo recebido; o objeto fica no bucket separado e pode ser removido pela porta.

- [ ] **C-016 [FR-006][FR-010][FR-013]** Testar a orquestração e a publicação.
  - Verificação: staging precede publicação; falha no staging não publica; a mensagem
    não transporta binário; a resposta aguarda a confirmação definida pelo contrato.

- [ ] **C-017 [FR-001..FR-013]** Executar regressão do backend existente.
  - Verificação: persistência PostgreSQL, consulta por ID, estados, reconciliação,
    storage legado e contexto Spring passam; a quebra do JSON é confirmada como mudança
    intencional apenas se estiver na revisão aprovada.

- [ ] **C-018 [FR-006][FR-010][FR-013]** Executar integração RabbitMQ quando a feature de
  mensageria fornecer broker, topologia e publisher reais.
  - Verificação: mensagem persistente chega à fila correta, publisher confirm é respeitado
    e o consumidor consegue ler o staging pela referência.

## Fase 5 — Entrega

- [ ] **C-019 [FR-001..FR-013]** Versionar a implementação e limpar o worktree.
  - Verificação: `git diff --check` não apresenta erros; `git status --short` não mostra
    alterações não aprovadas, arquivos temporários ou arquivos de feature esquecidos.

- [ ] **C-020 [FR-001..FR-013]** Atualizar a documentação e o checklist de convergência.
  - Verificação: Spec, plano, tarefas, código e testes descrevem o mesmo comportamento;
    divergências residuais estão registradas e a arquitetura não foi ampliada sem ADR.

- [ ] **C-021 [FR-001][FR-007][FR-009][FR-013]** Revisar o rollback por revisão Git.
  - Verificação: o procedimento registra a revisão estável antes do `pull`, recompila
    essa revisão, restaura o serviço sem apagar volumes/objetos e orienta o tratamento de
    staging pendente.

- [ ] **C-022 [FR-001..FR-013]** Solicitar aprovação final antes do deploy.
  - Verificação: Marcos revisa o diff final, os testes prioritários, os riscos e o
    bloqueio/solução de RabbitMQ; nenhuma execução de deploy ocorre antes dessa aprovação.

## Critérios para encerrar esta rodada

Esta rodada só pode ser considerada concluída quando:

1. o `HEAD` promovido tiver entrada multipart real;
2. o staging tiver escrita e leitura provider-neutral no MinIO;
3. o conteúdo puder ser recuperado por `contentReference`;
4. os testes prioritários e a regressão passarem;
5. a publicação RabbitMQ estiver implementada, caso a Spec permaneça exigindo publicação
   antes da resposta HTTP;
6. o checklist de convergência for revisado novamente;
7. Marcos aprovar explicitamente o deploy.

