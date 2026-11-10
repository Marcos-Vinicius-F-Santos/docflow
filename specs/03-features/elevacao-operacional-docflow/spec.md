# Spec da Feature — Elevar o DocFlow para uma entrega demonstrável de engenharia

Status: Rascunho — pronta para revisão e aprovação  
Projeto: DocFlow  
Onde vive no código: repositório inteiro, com incrementos em `backend/`, `frontend/`, `infra/`, `docs/`, `.github/` e `specs/`.

## 1. Objetivo

Evoluir o DocFlow, em um único plano dividido por fases, para demonstrar de forma
reproduzível backend Java, mensageria, CI/CD, cloud, observabilidade, segurança,
escala e operação, sem apresentar o projeto como experiência profissional pessoal.

Cada fase deve ser uma entrega completa e funcional, encerrada por um commit próprio.
Uma fase só poderá ser iniciada depois que o commit da fase anterior tiver seus testes,
critérios de aceite e evidências verificados. O plano não autoriza código antes da
aprovação desta Spec.

## 2. Como funciona hoje

Esta seção registra o baseline verificado no worktree local e no repositório remoto
`Marcos-Vinicius-F-Santos/docflow` no GitHub.

### Produto e execução local

- O backend Java/Spring Boot expõe registro, consulta, listagem, retry, download,
  preview e exclusão de documentos.
- O frontend Angular permite upload, acompanhamento, listagem, download, retry e
  exclusão.
- O fluxo recebe `multipart/form-data`, grava o conteúdo em staging no MinIO, publica
  uma referência no RabbitMQ, grava o conteúdo no storage final e confirma o documento
  depois da operação correspondente.
- PostgreSQL persiste metadados e estado; MinIO armazena o binário; RabbitMQ transporta
  o processamento assíncrono.
- O domínio usa `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`, além da reconciliação
  de resultados `RESULT_UNKNOWN`.
- Há retries, DLQ, publisher confirms, consumidor com concorrência/prefetch
  configurados, scheduler de reconciliação e logs operacionais persistidos com consulta
  read-only em `/logs`.
- Há WebSocket de notificações e e-mail opcional por adaptador provider-neutral.

### Testes e baseline

- O projeto possui testes unitários, testes Angular e testes de integração com
  Testcontainers.
- A documentação declara backend e frontend validados localmente e infraestrutura
  local reproduzível por Docker Compose.
- A quantidade atual de testes, cobertura efetiva, duração e limitações ainda não estão
  registradas em um baseline versionado e reproduzido por uma execução limpa. A Fase 0
  deverá produzir esse registro.

### CI/CD, deploy e cloud

- Não existe `.github/workflows/` no worktree nem no `main` do GitHub.
- Não existe pipeline específico do DocFlow para backend, frontend, infraestrutura ou
  integração.
- Não existe imagem Docker publicada do backend nem deploy externo configurado.
- O Docker Compose local é o ambiente operacional atual.
- O README declara deploy externo fora do escopo atual.

### Segurança e tenancy

- Os endpoints atuais não exigem autenticação.
- Não foram encontrados Spring Security, JWT/OAuth2, `SecurityFilterChain`, `owner_id`,
  tenant persistido ou autorização por usuário.
- O sistema opera como uma instalação única e compartilhada.
- CORS e origens permitidas existem apenas como configuração do WebSocket; isso não
  constitui autenticação ou autorização da API.

### Observabilidade e escala

- O backend possui Actuator, logs estruturados e consulta de logs operacionais.
- Não foram encontrados Micrometer, endpoint Prometheus, dashboards Grafana ou alertas
  de métricas.
- Não há relatório de carga versionado com throughput, latência P50/P95/P99, memória,
  tempo de fila e taxa de erro.

### Repositório e referência remota

- O worktree está na branch `main`, sem alterações locais reportadas, e acompanha o
  mesmo commit da branch `main` no GitHub: `bb908044d5032751c4ef2d2403fcb6b3fdc33e73`.
- A estrutura `specs/03-features/FEATURE_TEMPLATE/spec.md` não existe localmente nem no
  GitHub. Esta Spec usa a estrutura das specs de features existentes como referência.

Fontes do baseline:

- `README.md`;
- `specs/01-produto/SPEC_PRODUTO.md`;
- `specs/02-arquitetura/ARQUITETURA.md` e seus ADRs;
- `backend/src/main/java/.../document/controller/`;
- `backend/src/main/resources/application.properties`;
- repositório remoto `https://github.com/Marcos-Vinicius-F-Santos/docflow`.

## 3. Requisitos funcionais

### FR-001 — Baseline limpo e reproduzível

QUANDO a Fase 0 for executada em um checkout limpo do DocFlow  
O SISTEMA DEVE executar a suíte backend com Docker/Testcontainers ativo, executar
`npm ci`, lint, build e testes do frontend, corrigir a resolução de `@angular/core` no
Vitest, corrigir o alerta de orçamento CSS e registrar testes, cobertura e limitações
observadas.

QUANDO a Fase 0 for encerrada  
O SISTEMA DEVE atualizar a documentação para separar explicitamente o que foi validado
localmente, o que foi validado em CI e o que foi publicado externamente.

### FR-002 — CI do projeto

QUANDO houver pull request ou push na branch principal  
O SISTEMA DEVE executar um workflow de CI específico do DocFlow que faça checkout,
configure Java 21, execute `./mvnw verify`, execute os testes Testcontainers, configure
Node/npm, execute `npm ci`, lint, testes e build do frontend, construa a imagem Docker,
verifique segredos no bundle e execute scan básico de dependências e imagem.

QUANDO o workflow de CI terminar com sucesso  
O SISTEMA DEVE publicar seu status de forma identificável no repositório e no README.

### FR-003 — Imagem versionada e deploy de staging

QUANDO o CI estiver verde para uma revisão elegível  
O SISTEMA DEVE publicar uma imagem Docker versionada pelo commit no GitHub Container
Registry e permitir o deploy do backend em um ambiente de staging.

QUANDO o deploy de staging for executado  
O SISTEMA DEVE aplicar Flyway, verificar `/actuator/health`, executar um smoke test de
upload real, confirmar o processamento RabbitMQ, confirmar o documento no storage,
confirmar consulta, download e exclusão, e registrar o commit e o resultado do deploy.

QUANDO for necessário desfazer um deploy de staging  
O SISTEMA DEVE permitir rollback para a imagem anterior conforme procedimento
documentado.

### FR-004 — Ambiente cloud demonstrável

QUANDO a implantação cloud for concluída  
O SISTEMA DEVE disponibilizar frontend público, backend de staging protegido,
PostgreSQL externo, RabbitMQ externo e storage S3-compatible externo, com HTTPS e
segredos fora do repositório.

QUANDO o fluxo de upload for executado no ambiente cloud  
O SISTEMA DEVE completar o mesmo fluxo funcional do ambiente local, incluindo staging,
processamento assíncrono, consulta, download e exclusão.

QUANDO uma falha exigir retorno operacional  
O SISTEMA DEVE ter rollback documentado e executado pelo menos uma vez antes de a fase
ser considerada concluída.

### FR-005 — Métricas e observabilidade

QUANDO a Fase 3 estiver ativa  
O SISTEMA DEVE expor métricas por Micrometer e endpoint compatível com Prometheus para
uploads aceitos, documentos concluídos, falhas, retries, mensagens em DLQ, tempo de
processamento, tempo de upload, documentos em `PROCESSING`, ocorrências de
`RESULT_UNKNOWN`, falhas de PostgreSQL/RabbitMQ/MinIO ou S3 e taxa de erro HTTP.

QUANDO houver uma operação distribuída entre API, banco, mensageria e storage  
O SISTEMA DEVE preservar um correlation ID em todas as etapas observáveis.

QUANDO o dashboard e os alertas forem configurados  
O SISTEMA DEVE disponibilizar dashboard Grafana, export ou screenshot verificável e
alertas para DLQ acima do limite, crescimento de `PROCESSING`, falha repetida de
storage, P95 de processamento acima do limite, health degradado e erros HTTP acima do
limite.

QUANDO um alerta for provocado intencionalmente  
O SISTEMA DEVE registrar a ocorrência e disponibilizar runbook com diagnóstico e
recuperação.

### FR-006 — Autenticação, autorização e multi-tenant

QUANDO um cliente acessar endpoint sensível após a Fase 4  
O SISTEMA DEVE exigir autenticação baseada em Spring Security e JWT/OAuth2, conforme o
provedor aprovado para a implementação.

QUANDO um usuário autenticado operar documentos  
O SISTEMA DEVE associar cada documento ao usuário ou tenant responsável e impedir que o
usuário visualize, baixe, tente novamente, exclua ou consulte logs fora do próprio
escopo autorizado.

QUANDO dois usuários enviarem documentos com o mesmo nome  
O SISTEMA DEVE manter os documentos isolados, permitindo a cada usuário acessar apenas
os próprios documentos.

QUANDO um usuário exercer o perfil `USER`, `OPERATOR` ou `ADMIN`  
O SISTEMA DEVE aplicar autorizações distintas para upload, download, retry, logs e
exclusão.

QUANDO o cliente acessar WebSocket, CORS, upload ou retry  
O SISTEMA DEVE aplicar proteção de conexão, origens por ambiente, rate limiting e
políticas seguras para nomes e tipos de arquivo.

QUANDO uma ação sensível for executada  
O SISTEMA DEVE registrar auditoria suficiente para identificar ator, ação, alvo,
resultado e instante, sem registrar segredos ou conteúdo binário.

### FR-007 — Carga, falhas e resiliência

QUANDO forem executados os cenários de carga e resiliência  
O SISTEMA DEVE testar uploads concorrentes, documentos grandes, mensagens duplicadas,
RabbitMQ indisponível, storage indisponível, reinício do backend, retry após falha e
reconciliação de `RESULT_UNKNOWN`.

QUANDO cada cenário for executado  
O SISTEMA DEVE registrar throughput, latência P50/P95/P99, uso de memória, tempo de
fila, quantidade de retries e taxa de erro, sem publicar números que não tenham sido
medidos na execução real.

### FR-008 — Contratos e operação

QUANDO a Fase 6 for concluída  
O SISTEMA DEVE disponibilizar OpenAPI/Swagger, exemplos de requests e responses,
readiness probe, liveness probe, versionamento da API, runbook de incidentes,
procedimento de backup e restauração do PostgreSQL, política de retenção de documentos,
política de limpeza de objetos órfãos, diagrama de arquitetura, diagrama do fluxo
assíncrono, rollback, restore e decisões arquiteturais relacionadas.

QUANDO uma pessoa consultar o pacote final de evidências  
O SISTEMA DEVE permitir relacionar requisito de vaga a evidência real, incluindo README,
frontend, backend, CI, imagem, dashboard, carga, segurança, runbook e cenários de falha
e recuperação.

## 4. Organização das fases e commits

Cada fase abaixo é uma unidade de entrega. O commit correspondente deve conter código,
configuração, testes e documentação necessários para a fase funcionar por completo. Não
será considerado concluído um commit que apenas crie a estrutura para uma capacidade
futura sem demonstrá-la.

### Fase 0 — Estabilização e baseline

Objetivo: tornar a execução local reproduzível e registrar o ponto de partida.

Entrega mínima:

- frontend com lint, build e testes verdes;
- backend com testes unitários e integração verdes usando Docker/Testcontainers;
- correção de `@angular/core` no Vitest;
- correção do orçamento CSS;
- relatório versionado com quantidade de testes, cobertura, duração e limitações;
- README separando validação local, CI e publicação externa.

Commit completo: deve ser executável localmente a partir de checkout limpo e produzir o
relatório de baseline.

### Fase 1 — CI/CD próprio do DocFlow

Objetivo: automatizar validação, publicação da imagem e deploy de staging.

Entrega mínima:

- `.github/workflows/ci.yml`;
- `.github/workflows/deploy-staging.yml`;
- badge de CI no README;
- imagem no GitHub Container Registry;
- smoke test automatizado;
- aplicação de Flyway, health check, upload, processamento, consulta, download e
  exclusão no staging;
- rollback para a imagem anterior;
- links e logs de evidência.

Dependência: commit da Fase 0 aprovado.

### Fase 2 — Ambiente cloud demonstrável

Objetivo: reproduzir o fluxo em ambiente externo separado do desenvolvimento local.

Entrega mínima:

- frontend Angular publicado;
- backend containerizado em staging;
- PostgreSQL, RabbitMQ e storage S3-compatible externos;
- HTTPS;
- segredos em configuração do provedor;
- URL pública do frontend e URL protegida do backend;
- rollback cloud documentado e exercitado.

Dependência: CI/CD da Fase 1 aprovado.

### Fase 3 — Observabilidade real

Objetivo: transformar logs e health checks existentes em métricas, dashboard, alertas e
procedimentos de resposta.

Entrega mínima:

- Micrometer e endpoint Prometheus;
- métricas dos fluxos HTTP, RabbitMQ, storage, upload e processamento;
- correlation ID;
- Grafana com dashboard exportável;
- alertas provocados intencionalmente;
- runbook de diagnóstico e recuperação.

Dependência: ambiente da Fase 2 ou ambiente equivalente capaz de demonstrar as métricas.

### Fase 4 — Segurança e multi-tenant

Objetivo: proteger a API e isolar documentos e operações por usuário/tenant.

Entrega mínima:

- autenticação Spring Security com JWT/OAuth2;
- perfis `USER`, `OPERATOR` e `ADMIN`;
- `owner_id` ou modelo equivalente aprovado;
- isolamento de consulta, download, retry, logs e exclusão;
- proteção do WebSocket;
- CORS por ambiente;
- rate limiting para upload e retry;
- auditoria de ações sensíveis;
- políticas de nomes e tipos de arquivo;
- testes de acesso indevido e verificação contra riscos OWASP.

Dependência: decisão explícita do provedor de identidade, do modelo de tenant e das
regras de autorização antes da implementação.

### Fase 5 — Escala, carga e resiliência

Objetivo: medir comportamento sob carga e demonstrar recuperação de falhas controladas.

Entrega mínima:

- cenários automatizados com k6 ou Gatling;
- uploads concorrentes e documentos grandes;
- duplicidade de mensagens;
- indisponibilidade e reinício de RabbitMQ, storage e backend;
- retries e reconciliação de `RESULT_UNKNOWN`;
- relatório com resultados reais e limitações.

Dependência: observabilidade da Fase 3 e fluxo protegido da Fase 4 quando a carga
representar endpoints autenticados.

### Fase 6 — Contratos e operação

Objetivo: tornar o produto operável, documentado e auditável por outra pessoa.

Entrega mínima:

- OpenAPI/Swagger e exemplos;
- readiness/liveness separados;
- versionamento da API;
- runbooks;
- backup/restore;
- retenção de documentos;
- limpeza de objetos órfãos;
- diagramas;
- decisões arquiteturais;
- rollback e restore verificados.

Dependência: comportamento das fases anteriores estabilizado.

### Fase 7 — Lacunas específicas de stack

Esta fase não faz parte do caminho obrigatório da evolução principal. MongoDB, Python ou
Kafka só poderão entrar no DocFlow se houver um caso de uso real, uma spec própria e
uma decisão arquitetural aprovada. C#, .NET, Oracle/PL-SQL, Kotlin, Next.js,
Power Automate/Automation Anywhere e Rust/Nix permanecem candidatos a projetos
separados.

### Fase 7.5 — Validação manual do fluxo e correções

Esta fase ocorre antes do pacote final de evidências. O responsável pelo projeto
executará o fluxo publicado e o ambiente local, registrando evidências de cada etapa.
Problemas encontrados deverão ser reproduzidos, corrigidos com escopo mínimo,
testados e revalidados antes do encerramento da fase. A Fase 8 não deve consolidar
afirmações sobre o produto enquanto esta validação estiver pendente.

### Fase 8 — Pacote final de evidências

Esta fase consolida, sem inventar resultados, o README, URLs, badges, imagem, dashboard,
relatório de carga, cenários de falha, testes de segurança, runbooks, diagramas e a
matriz requisito da vaga → evidência real.

## 5. Regras de negócio e de entrega

### BR-001 — Uma fase é indivisível para fins de entrega

O commit da fase deve deixar a capacidade da fase funcionando, testada e documentada.
Estruturas incompletas ou código desabilitado não constituem entrega.

### BR-002 — Ordem incremental

As fases devem ser implementadas na ordem Fase 0 → Fase 1 → Fase 2 → Fase 3 → Fase 4
→ Fase 5 → Fase 6. As Fases 7, 7.5 e 8 dependem de decisões e da maturidade do pacote
principal.

### BR-003 — Evidência real

Nenhum teste, número de carga, URL, dashboard, rollback ou publicação pode ser descrito
como concluído sem execução real e registro verificável.

### BR-004 — Segredos fora do repositório

Credenciais, tokens, chaves, senhas e valores sensíveis devem vir de secrets ou variáveis
de ambiente. O bundle do frontend não pode conter segredos de backend.

### BR-005 — Compatibilidade e migração

Mudanças de schema devem usar migrations Flyway. Mudanças de contrato, autenticação,
tenant ou deploy devem preservar caminho de migração, rollback e documentação.

### BR-006 — Não quebrar o fluxo atual sem contrato aprovado

O fluxo existente de upload, processamento, consulta, download, retry e exclusão deve
continuar funcionando, salvo mudança explícita nos requisitos da fase correspondente.

### BR-007 — Revisão humana

Marcos é o aprovador final da Spec, de cada commit de fase, dos critérios de aceite e de
qualquer deploy. A conclusão técnica não autoriza deploy automaticamente.

## 6. Critérios de aceite

### AC-001 — Fase 0, caminho feliz

Dado um checkout limpo sem serviços externos previamente configurados  
Quando a pessoa seguir a documentação da Fase 0 e executar a suíte backend e frontend  
Então lint, build e testes devem terminar com sucesso, o fluxo local deve continuar
funcionando e o relatório de baseline deve registrar testes, cobertura e limitações.

### AC-002 — Fase 0, caminho de erro

Dado um ambiente sem RabbitMQ, MinIO ou PostgreSQL externos configurados  
Quando os testes forem executados  
Então os testes unitários não devem tentar conexões externas silenciosas e os testes de
integração devem usar dependências gerenciadas pela própria suíte ou falhar com
diagnóstico explícito.

### AC-003 — Fase 1, CI e deploy

Dado um pull request aprovado no código da Fase 1  
Quando o workflow de CI for executado e terminar verde  
Então backend, Testcontainers, frontend, imagem, scan e verificação de segredos devem
ser executados, e o deploy de staging subsequente deve publicar a imagem, aplicar Flyway,
validar health, executar o smoke test completo e registrar o commit.

### AC-004 — Fase 1, falha e rollback

Dado um deploy de staging que falhe no health check, upload, processamento, download ou
exclusão  
Quando o procedimento de recuperação for executado  
Então o sistema deve identificar a versão anterior, retornar à imagem anterior e deixar
um registro do resultado do rollback.

### AC-005 — Fase 2, caminho feliz cloud

Dado o frontend e backend publicados em staging com PostgreSQL, RabbitMQ e storage
externos configurados  
Quando uma pessoa enviar um documento válido  
Então o documento deve percorrer staging, mensageria, storage final, consulta e download
sem depender de serviços da máquina local.

### AC-006 — Fase 2, configuração insegura

Dado um deploy sem uma variável obrigatória ou com segredo ausente  
Quando o ambiente iniciar  
Então o deploy deve falhar de modo explícito, sem usar credencial hardcoded, e o health
ou log operacional deve indicar a configuração ausente sem revelar seu valor.

### AC-007 — Fase 3, métricas e alerta

Dado um ambiente observável com dashboard e alertas ativos  
Quando forem executados uploads, processamento, retry, falha de storage e mensagens em
DLQ  
Então as métricas correspondentes devem aparecer com correlation ID, o dashboard deve
refletir o fluxo e pelo menos um alerta provocado intencionalmente deve gerar evidência
e apontar para um runbook.

### AC-008 — Fase 3, health degradado

Dado que RabbitMQ ou storage esteja indisponível  
Quando a aplicação receber uma operação afetada  
Então o health e as métricas devem indicar degradação, o fluxo não deve reportar sucesso
falso e o runbook deve descrever diagnóstico e recuperação.

### AC-009 — Fase 4, isolamento entre usuários

Dado dois usuários autenticados que enviaram documentos com o mesmo nome  
Quando cada usuário consultar, baixar, tentar novamente ou excluir seus documentos  
Então cada usuário deve acessar somente o próprio escopo autorizado.

### AC-010 — Fase 4, acesso indevido

Dado um usuário autenticado tentando acessar o documento, log ou operação de outro
usuário sem permissão  
Quando a requisição for processada  
Então o sistema deve rejeitá-la conforme o contrato de autorização, registrar a tentativa
de forma segura e não revelar metadados indevidos.

### AC-011 — Fase 5, relatório de carga

Dado um cenário real de 50 uploads concorrentes ou outro limite aprovado antes da
execução  
Quando o teste de carga e resiliência for executado  
Então o relatório deve apresentar throughput, P50/P95/P99, memória, fila, retries e taxa
de erro medidos, além de demonstrar ausência de duplicação indevida e recuperação após
falha controlada.

### AC-012 — Fase 5, reinício durante processamento

Dado que o backend, RabbitMQ ou storage seja reiniciado durante o processamento  
Quando os componentes retornarem e as políticas de retry/reconciliação forem executadas
  
Então o documento deve terminar em estado consistente, sem sucesso falso, perda
silenciosa da mensagem ou duplicação indevida do objeto.

### AC-013 — Fase 6, operação documentada

Dado uma pessoa desenvolvedora que não conhece previamente o projeto  
Quando ela consultar OpenAPI, runbooks, diagramas e procedimentos de backup, restore e
rollback  
Então deverá conseguir identificar contratos, dependências, diagnóstico, recuperação e
limites atuais sem depender de conhecimento oral.

### AC-014 — Regressão entre fases

Dado um commit de qualquer fase posterior  
Quando a regressão mínima do DocFlow for executada  
Então upload, processamento RabbitMQ, consulta, download, retry, exclusão e frontend
devem continuar funcionando conforme os contratos aprovados, ou a mudança incompatível
deve estar explicitamente descrita na fase.

## 7. Fora de escopo

- Apresentar o DocFlow como experiência profissional real.
- Inventar métricas, URLs, aprovações, resultados de carga ou incidentes.
- Adicionar tecnologias apenas para preencher palavras-chave sem caso de uso.
- Colocar C#/.NET, Oracle/PL-SQL, Kotlin, Next.js, Power Automate/Automation Anywhere,
  Rust ou Nix dentro do DocFlow sem nova decisão e spec própria.
- Criar microserviços, Kubernetes ou Kafka sem necessidade confirmada pela fase e sem
  decisão arquitetural aprovada.
- Escolher silenciosamente provedor cloud, provedor de identidade, ferramenta de carga,
  limites de alerta, modelo de tenant ou política de retenção.
- Fazer deploy de produção antes da revisão e aprovação explícita de Marcos.

## 8. Dependências e impactos arquiteturais

As fases de CI/CD, cloud, observabilidade, segurança e carga alteram limites atualmente
fora do escopo do produto e da arquitetura. Antes do código correspondente, cada fase
deve atualizar, quando necessário:

- `specs/01-produto/SPEC_PRODUTO.md`;
- `specs/02-arquitetura/ARQUITETURA.md`;
- ADRs de cloud, segurança, observabilidade, mensageria ou tenancy;
- `docs/api.md`, runbooks, diagramas e documentação operacional.

O limite atual de API → aplicação → portas → adaptadores deve ser preservado. O frontend
não deve acessar PostgreSQL, RabbitMQ ou storage diretamente. Segredos devem permanecer
fora do código e do bundle.

## 9. Evidências esperadas por fase

| Fase | Evidência mínima |
|---|---|
| 0 | execução limpa, relatório de testes/cobertura/limitações e documentação local |
| 1 | workflows, badge, imagem versionada, logs do smoke test e rollback |
| 2 | URLs, health protegido, fluxo cloud, configuração de secrets e rollback executado |
| 3 | dashboard, export/screenshot, métricas, alerta provocado e runbook |
| 4 | testes de autorização, isolamento entre usuários, auditoria e verificação OWASP |
| 5 | relatório de carga e falhas com números medidos e limitações |
| 6 | OpenAPI, runbooks, backup/restore, diagramas e matriz de evidências |
| 8 | pacote final consolidado sem resultados não executados |

## 10. Suposições e perguntas abertas

As decisões abaixo não foram tomadas silenciosamente; precisam ser confirmadas antes da
implementação da fase que as utiliza.

### Suposições usadas para estruturar o plano

- A intenção é manter uma única Spec mestre para a evolução e não criar uma feature
  isolada por fase.
- Cada commit de fase pode conter alterações em múltiplas camadas, desde que a fase seja
  funcional de ponta a ponta.
- Fase 0 e Fase 1 são o primeiro caminho obrigatório; Fases 7 e 8 não bloqueiam o
  primeiro deploy demonstrável.
- O baseline confirmado no commit `bb908044d5032751c4ef2d2403fcb6b3fdc33e73` é a
  referência de comparação até que outro commit seja aprovado.
- A estrutura das specs existentes substitui temporariamente o template ausente.

### Perguntas abertas

- Qual provedor cloud deverá ser usado na Fase 2?
- O frontend continuará no GitHub Pages ou será publicado junto ao provedor cloud?
- Qual provedor de identidade será usado para JWT/OAuth2?
- O isolamento será por usuário, por tenant organizacional ou por ambos?
- Quais permissões exatas diferenciam `USER`, `OPERATOR` e `ADMIN`?
- Qual ferramenta será adotada para carga: k6 ou Gatling?
- Quais limites numéricos devem disparar cada alerta?
- Qual é o limite de upload, tamanho de documento, throughput e latência aceitável para
  o primeiro ambiente de staging?
- Qual política de retenção de documentos e limpeza de objetos órfãos será aprovada?
- Qual convenção de nome e mensagem de commit será usada para as fases?
- O commit de cada fase será criado localmente para revisão ou também publicado no
  GitHub após aprovação?
- O CI deve rodar em todo push de branch ou apenas em pull request e `main`?
- O deploy de staging deve ser automático após CI verde ou depender de aprovação manual?

## 11. Documentos de implementação, validação e execução

Cada fase possui documentos operacionais próprios, separados conforme o padrão do
projeto:

| Fase | Implementação | Validação | Execução |
|---|---|---|---|
| 0 | `specs/04-plano/elevacao-operacional-docflow/fase-0/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-0/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-0/execucao.md` |
| 1 | `specs/04-plano/elevacao-operacional-docflow/fase-1/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-1/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-1/execucao.md` |
| 2 | `specs/04-plano/elevacao-operacional-docflow/fase-2/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-2/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-2/execucao.md` |
| 3 | `specs/04-plano/elevacao-operacional-docflow/fase-3/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-3/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-3/execucao.md` |
| 4 | `specs/04-plano/elevacao-operacional-docflow/fase-4/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-4/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-4/execucao.md` |
| 5 | `specs/04-plano/elevacao-operacional-docflow/fase-5/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-5/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-5/execucao.md` |
| 6 | `specs/04-plano/elevacao-operacional-docflow/fase-6/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-6/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-6/execucao.md` |
| 7 | `specs/04-plano/elevacao-operacional-docflow/fase-7/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-7/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-7/execucao.md` |
| 7.5 | `specs/04-plano/elevacao-operacional-docflow/fase-7-5/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-7-5/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-7-5/execucao.md` |
| 8 | `specs/04-plano/elevacao-operacional-docflow/fase-8/tarefas.md` | `specs/05-verificacao/elevacao-operacional-docflow/fase-8/checklist.md` | `specs/06-deploy/elevacao-operacional-docflow/fase-8/execucao.md` |

O índice geral está em `specs/04-plano/elevacao-operacional-docflow/README.md`.

## 12. Aprovação

- [ ] Marcos revisou e aprovou o objetivo e o escopo da Spec.
- [ ] Marcos confirmou as perguntas abertas que afetam a próxima fase.
- [ ] Marcos aprovou a Fase 0 para implementação.
- [ ] Marcos revisará e aprovará cada commit de fase antes do deploy correspondente.

Esta Spec descreve o plano e os critérios de aceitação. Nenhum código ou deploy deve ser
produzido apenas com base na existência deste documento; a implementação começa depois
da aprovação explícita de Marcos.
