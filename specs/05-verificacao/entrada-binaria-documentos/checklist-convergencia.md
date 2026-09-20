# Checklist de Convergência

Feature: Recebimento de conteúdo binário no registro de documentos

Data da revisão: 2026-09-16

Status: revisão corrigida; deploy bloqueado por divergência de implementação.

O template solicitado em `specs/05-verificacao/checklist-convergencia.md` não existe
literalmente neste repositório. Este checklist segue o formato do modelo disponível em
`specs/05-verificacao/politica-reconciliacao-result-unknown/checklist-convergencia.md`.

## Requisitos

- [ ] **Divergência.** No `HEAD` versionado, FR-001, FR-002, FR-003 e FR-004 não estão
  refletidos: o controller ainda recebe `@RequestBody` JSON e não existe entrada
  multipart/binária na API.
- [ ] **Não confirmado.** Os testes e tipos multipart encontrados estão apenas nas
  alterações locais do worktree, algumas não rastreadas; não fazem parte da revisão que
  o procedimento de deploy consegue obter via Git.
- [ ] AC-001, AC-002, AC-004, AC-005, AC-006, AC-008 e AC-009 não podem ser atribuídos ao
  artefato versionado atual. Precisam ser revalidados depois que a implementação for
  commitada e selecionada para deploy.
- [ ] **Não confirmado integralmente.** AC-003, AC-007 e FR-013 continuam sem execução
  ponta a ponta; além do wiring ausente, não há leitura do objeto de staging disponível
  para o consumidor.
- [ ] **Divergência.** BR-003 descreve o contrato interno como acompanhado do tamanho
  real, mas a implementação versionada não possui o contrato binário correspondente.

## Arquitetura

- [ ] **Divergência.** A arquitetura provider-neutral para staging aparece somente nas
  alterações locais; o código versionado expõe apenas o adaptador de storage existente.
- [ ] Não há leitura do staging por uma porta/adaptador disponível no código versionado.
- [ ] Não existe configuração/bean para `MinioClient`, `DocumentStaging` ou
  `DocumentStorageRequestPublisher` no contexto versionado.
- [x] A ADR-004 foi aceita, mas isso não substitui a implementação das portas e
  adaptadores definidos nela.

## Dados e operação

- [x] A feature não introduz migration nem alteração de schema versionada para o fluxo
  binário.
- [ ] Não foi confirmado contrato de leitura do staging, nem o fluxo que transforma a
  referência em conteúdo para o consumidor.
- [ ] **Não confirmado integralmente.** A persistência PostgreSQL do caminho binário não
  foi validada em integração. Os testes de integração existentes cobrem o caminho antigo
  de metadados, não a nova chamada com staging/publicação.
- [ ] **Não confirmado no revisionamento.** As propriedades preventivas
  `spring.servlet.multipart.max-file-size` e `spring.servlet.multipart.max-request-size`
  foram adicionadas localmente, mas ainda não estão confirmadas na revisão Git que será
  obtida pelo deploy.
- [x] Existe procedimento documentado de preparação e rollback em
  `docs/document-registration-operations.md`, sem remoção em massa ou edição direta do
  banco.

## Testes

- [ ] **Não confirmado formalmente.** Não existe plano de verificação específico da
  feature em `specs/05-verificacao/`; portanto, a lista oficial de requisitos de
  prioridade alta não pôde ser identificada no plano. A cobertura foi confrontada com
  os FRs, ACs e verificações de `tarefas.md`.
- [ ] A suíte local anteriormente executada passou com 49 testes, mas parte desses testes
  pertence às alterações não commitadas; isso não confirma o artefato versionado atual.
- [ ] A cobertura direcionada de multipart, staging e publisher precisa ser executada
  novamente após a implementação ser incorporada à revisão de deploy.
- [ ] **Não confirmado.** T-021 não foi uma integração RabbitMQ real; foi um teste do
  contrato provider-neutral, porque não há publisher, topologia, exchange/fila ou
  confirmação RabbitMQ implementados no código.
- [ ] Não há confirmação de que a quebra do POST JSON seja intencional no artefato
  versionado; no `HEAD`, o contrato JSON ainda existe.
- [ ] **Não confirmado.** Não foi possível confirmar o caminho feliz completo pela API
  real até staging e mensageria, pois as portas opcionais permanecem sem implementações
  registradas no contexto de produção.

## Entrega

- [ ] **Não confirmado.** A configuração e operação completas não estão prontas para
  deploy: falta entrada binária versionada, leitura do staging, wiring de MinIO,
  publisher RabbitMQ e topologia operacional.
- [x] O rollback da quebra de contrato e de falhas entre PostgreSQL, MinIO e RabbitMQ
  está documentado, preservando objetos/mensagens e evitando ações destrutivas.
- [x] Os riscos e divergências foram registrados em
  `docs/document-registration-delivery-review.md`.
- [x] A ADR-004 foi aprovada.
- [x] Marcos aprovou o procedimento de deploy, mas esta aprovação não elimina a
  divergência entre o `HEAD` e a implementação esperada.

## Divergências encontradas

1. **Entrada binária ausente no código versionado:** o controller do `HEAD` ainda recebe
  `DocumentRegistrationRequest` via `@RequestBody`; não existe `MultipartFile` ou
  contrato binário na API versionada.

2. **Leitura do staging ausente:** o storage versionado não possui operação de leitura;
  só existem `store`, `exists` e `delete`.

3. **RabbitMQ ausente operacionalmente:** existe somente a decisão/contrato local; não
  há publisher, publisher confirm, exchange, fila ou integração real. O teste T-021 não
  comprova publicação no broker.

4. **Limite multipart ainda não incorporado ao revisionamento do deploy:** a propriedade
  foi configurada localmente, mas precisa estar no commit promovido.

5. **Implementação local não promovível:** os tipos de staging e a entrada multipart
  aparecem como arquivos não rastreados ou mudanças locais e não podem ser assumidos como
  parte do artefato obtido por `git pull`.

6. **Arquitetura geral ainda está em `Rascunho`:** a ADR-004 foi aceita, mas o limite
  arquitetural precisa ser aplicado no código que será promovido.

## Riscos e Registro de Decisão

- O risco de publicar/aceitar uma requisição sem staging ou publisher configurados é
  **alto**. Não exige nova ADR além da ADR-004, mas exige wiring executável e aprovação
  antes do deploy.
- A coordenação PostgreSQL–MinIO–RabbitMQ sem transação distribuída pode deixar objetos
  órfãos quando a persistência falhar depois do staging. O risco já está registrado na
  ADR-004 e no procedimento de rollback; nova compensação/outbox exigiria ADR própria.
- A quebra do contrato JSON é intencional, mas exige comunicação/coordenação com todos os
  clientes existentes e rollback preparado.
- A rejeição de formatos sem assinatura Tika conhecida pode produzir falso negativo; a
  Spec define a rejeição, portanto não é bloqueio adicional nesta revisão.

## Resultado da revisão

A camada de contrato, validação e testes unitários está alinhada em grande parte com a
Spec, e não houve indício de regressão nos 49 testes executados. A convergência completa
não pôde ser confirmada porque o fluxo de produção não possui wiring de staging/publisher,
RabbitMQ real não está implementado, o limite HTTP preventivo está ausente, existem
divergências de redação/declaração do contrato e a ADR-004 permanece pendente.

Este checklist registra a aprovação de Marcos, mas mantém o deploy **bloqueado** até que
a entrada binária, a leitura do staging e as dependências operacionais estejam presentes
na revisão Git promovida e sejam testadas novamente.
