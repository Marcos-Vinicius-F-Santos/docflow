# Plano de Implementação — Frontend Angular para o fluxo de documentos

Spec relacionada: specs/03-features/frontend-angular-docflow/spec.md  
Status: Rascunho

## 1. Resumo técnico

Criar a aplicação Angular dentro de `frontend/`, usando componentes standalone e
Angular Material. A feature de documentos ficará concentrada em
`frontend/src/app/document/`, com um serviço HTTP para `POST /documents` e
`GET /documents/{documentId}`, validação local do arquivo, normalização dos erros
`ProblemDetail`, polling de três segundos e telas para upload e acompanhamento.

O frontend falará somente com a API REST. MinIO, RabbitMQ e PostgreSQL permanecerão
inacessíveis ao navegador. O acesso local usará proxy configurável; não serão feitas
alterações de banco ou de contrato do backend nesta rodada.

## 2. Impacto no que já existe

| Componente/arquivo | Mudança | Risco |
|---|---|---|
| `frontend/` | Substituir o scaffold vazio por uma aplicação Angular executável e testável. | Médio: dependências e configuração inicial ainda não existem. |
| `frontend/src/app/document/` | Criar o limite da feature de documentos, seguindo a organização por domínio/feature. | Baixo: é uma área nova e não altera o backend. |
| `backend` | Nenhuma alteração funcional prevista; será usado como contrato externo. | Médio: divergências reais de HTTP, CORS ou ambiente podem bloquear a integração. |
| `docs/` | Atualizar somente se o comando de execução local, proxy ou contrato de uso do frontend precisar ser documentado. | Baixo. |
| `infra/` | Nenhuma alteração prevista nesta rodada. | Baixo; hospedagem externa permanece fora do escopo. |

Não serão alterados domínio, migrations, controllers, mensageria, storage ou regras de
processamento existentes. A pasta de feature Angular é suficiente para esta rodada;
uma camada `shared/` ou biblioteca de componentes só deverá ser criada quando houver
reuso real em duas ou mais features.

## 3. Componentes novos

- Aplicação Angular standalone e shell mínimo da aplicação.
- Rotas para a tela de novo upload e para a tela de acompanhamento por `documentId`.
- Modelos TypeScript para `DocumentResponse`, `DocumentStatus` e `ProblemDetail`.
- Serviço da feature para `POST /documents` e `GET /documents/{documentId}`.
- Validador local do arquivo, limitado a presença, nome, tipo informado, conteúdo
  vazio e `52.428.800` bytes.
- Mapeador de `errorCode` para mensagens de interface, com fallback genérico.
- Orquestrador de polling a cada três segundos, com atualização manual e parada em
  `COMPLETED`, `FAILED` ou `404`.
- Componente de seleção/arraste e revisão do arquivo.
- Componente de progresso do upload HTTP.
- Componente de detalhe e linha de estados `PENDING`, `PROCESSING`, `COMPLETED` e
  `FAILED`.
- Estados de carregamento, erro de validação, falha operacional e documento não
  encontrado.
- Configuração de ambiente/proxy sem credenciais e sem endpoints de infraestrutura.
- Testes unitários e de integração do frontend, sem reduzir a suíte existente do
  backend.

## 4. Mudança de dados/banco (se houver)

Não há mudança de dados ou banco. Nenhuma migration Flyway será criada ou alterada.

## 5. Sequência de implementação

### Fase 1 — Base

1. Inicializar o workspace Angular dentro de `frontend/`, fixando versões exatas das
   dependências no manifesto e mantendo a configuração compatível com Node/npm
   disponíveis no projeto.
2. Configurar componentes standalone, bootstrap, estilos globais, Angular Material,
   lint/testes e as rotas vazias da feature.
3. Definir o limite `document` em `frontend/src/app/document/`, modelos de contrato e
   configuração de URL base/proxy.
4. Documentar o comando local mínimo em `docs/frontend.md` e referenciar o documento
   no `README.md`.

**Gate da fase:** a aplicação inicia, compila e executa o teste mínimo sem modificar o
backend ou expor segredos.

### Fase 2 — Lógica principal

1. Implementar a validação local sem whitelist adicional de tipos e sem tentar substituir
   a validação por assinatura do backend.
2. Implementar o serviço HTTP de upload com `FormData`, usando somente `file`, e suporte
   a eventos de progresso do upload.
3. Implementar a consulta por identificador e o mapeamento de resposta para os quatro
   estados públicos do backend.
4. Implementar o polling RxJS a cada três segundos, atualização manual e encerramento
   em estado terminal ou `404`.
5. Implementar o mapeamento dos nove códigos de erro de validação e o tratamento de
   `404`, rede, timeout, `5xx` e respostas desconhecidas.
6. Garantir que falha após um `POST` não provoque reenvio automático potencialmente
   duplicado.

**Gate da fase:** os serviços e estados podem ser exercitados por testes sem depender
de componentes visuais ou de MinIO/RabbitMQ.

### Fase 3 — Interface

1. Criar a tela de upload com seleção/arraste, revisão dos metadados, validação local,
   bloqueio de submissão duplicada e mensagens acessíveis.
2. Criar a exibição de progresso do envio HTTP e a transição explícita de upload aceito
   (`PENDING`) para processamento assíncrono.
3. Criar a rota e a tela de acompanhamento por `documentId`, carregável diretamente
   após recarregar o navegador.
4. Criar a apresentação visual dos quatro estados sem representar `PENDING` ou
   `PROCESSING` como conclusão.
5. Criar ações de atualização manual, novo upload, nova consulta e tela de documento
   não encontrado.
6. Validar acessibilidade básica, responsividade e ausência de referências a serviços
   de infraestrutura no bundle ou na interface.

**Gate da fase:** o fluxo visual completo funciona contra o backend local, sem exigir
uma lista global, download, autenticação ou endpoint adicional.

### Fase 4 — Testes

1. Testar modelos, validador local, mapeador de mensagens e transições de estado.
2. Testar o serviço HTTP com mocks de `HttpClient`, incluindo multipart, progresso,
   sucesso, `ProblemDetail`, `404`, `5xx` e erro de rede.
3. Testar o polling, inclusive parada em `COMPLETED`, `FAILED` e `404`, refresh manual
   e permanência correta em `PROCESSING`.
4. Testar componentes de upload e detalhe com caminho feliz e erros prioritários.
5. Executar integração contra o backend local para validar o contrato real de
   `POST /documents` e `GET /documents/{documentId}`.
6. Executar a suíte existente do backend para confirmar ausência de regressão.

**Gate da fase:** os critérios AC-001 a AC-004 estão comprovados por testes e a suíte
do backend continua verde.

### Fase 5 — Entrega (deploy/rollback)

1. Executar build de produção e verificar que a URL da API vem da configuração de
   ambiente/proxy, sem segredos.
2. Executar o smoke test local: selecionar arquivo válido, enviar, observar `PENDING`,
   acompanhar `PROCESSING` e validar `COMPLETED` ou `FAILED` pelo backend real.
3. Verificar o caminho de erro com arquivo incompatível, documento inexistente e falha
   operacional simulada.
4. Atualizar `docs/frontend.md` com a execução local e adicionar o link correspondente
   no `README.md`.
5. Fazer revisão de convergência `Spec ↔ Plano/Tarefas ↔ Código ↔ Testes`.
6. Preservar a possibilidade de rollback removendo somente a versão do frontend ou
   revertendo seu artefato de build; não alterar banco, filas, buckets ou dados do
   backend como parte do rollback.
7. Aguardar a revisão e aprovação de Marcos antes de qualquer deploy.

**Gate da fase:** build, smoke test, testes automatizados e revisão final aprovados;
deploy externo continua fora do escopo desta feature.

## 6. Riscos

| Risco | Chance | Impacto | Como mitigar | Merece Registro de Decisão? |
|---|---|---|---|---|
| O frontend e o backend podem ser executados em origens diferentes, mas o backend não possui CORS configurado. | Média | Alto: o fluxo funciona localmente via proxy, mas falha em hospedagem separada. | Usar proxy local e preferir mesma origem; se uma implantação cross-origin for escolhida, abrir decisão específica antes de alterar CORS ou criar BFF. | Sim, se a arquitetura mudar para CORS/BFF ou outra borda de integração. |
| Polling a cada 3 segundos gera tráfego contínuo quando `PROCESSING` durar muito por reconciliação. | Média | Médio: custo de requisições e experiência de espera prolongada. | Parar em estados terminais/404, permitir atualização manual, manter mensagem clara e não criar websocket/SSE sem nova aprovação. | Não enquanto o comportamento aprovado permanecer; sim se for substituído por eventos em tempo real. |
| Uma resposta perdida após o `POST` pode significar que o documento foi aceito, e um retry automático criaria duplicidade. | Média | Alto: documentos duplicados e impossibilidade de idempotência pelo frontend atual. | Não repetir automaticamente; preservar a informação local disponível e exigir ação manual. | Não; a Spec já define esse comportamento. |
| O backend não informa motivo detalhado para `FAILED` nem oferece retry manual. | Alta | Médio: a interface não consegue orientar recuperação específica. | Exibir mensagem genérica e não inventar causa; criar nova feature/contrato se diagnóstico ou retry forem necessários. | Sim, se for criado um contrato de erro operacional ou reprocessamento. |
| A validação local pode divergir da validação Tika do backend. | Média | Médio: falsa aceitação ou rejeição antes do envio. | Manter apenas validações locais simples e tratar o backend como autoridade para assinatura e tipo. | Não; está coberto pela regra BR-001. |
| O workspace Angular parte de um diretório quase vazio e pode exigir ajustes de versão/tooling. | Alta | Médio: atraso na base ou build não reproduzível. | Fixar versões exatas, validar Node/npm disponíveis e concluir o gate da Fase 1 antes da lógica. | Não, salvo mudança de stack ou integração estrutural. |

## 7. Perguntas abertas antes de começar

- [x] Ambiente confirmado: Node `v24.19.0` e npm `12.0.2` estão disponíveis. A versão
  exata do Angular será fixada na T-001 conforme compatibilidade oficial com esse
  ambiente, sem inventar uma versão incompatível antecipadamente.
- [x] Escopo de execução confirmado: nesta rodada o frontend será executado localmente.
  O build de produção será validado, mas não haverá hospedagem nem deploy externo.
  Hospedagem em origem separada, CORS ou BFF exigirá nova decisão antes de ser criada.
- [x] Destino da documentação definido: comandos e configuração do frontend ficarão
  em `docs/frontend.md`, com link no `README.md`.
- [x] Smoke test confirmado: o ambiente possui Docker funcional e poderá executar o
  backend local completo com PostgreSQL, RabbitMQ e MinIO usando o Compose existente,
  `infra/.env` e o bootstrap aprovado.

As decisões acima resolvem as perguntas de planejamento. CORS, BFF, deploy externo e
novos endpoints continuam fora desta feature.
