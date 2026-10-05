# Tarefas — Frontend Angular para o fluxo de documentos

Plano relacionado: specs/04-plano/frontend-angular-docflow/plano.md

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente para revisar de uma vez, referenciar o
requisito `FR-XXX` que implementa e dizer como verificar que ficou pronta. A ordem
abaixo é obrigatória; nenhuma tarefa de interface deve começar enquanto os contratos e
os serviços da Fase 2 não estiverem verificáveis.

## Fase 1 — Base

- [ ] T-001 [FR-001][FR-015][FR-016] Inicializar a aplicação Angular standalone dentro de `frontend/`, com versões exatas de Angular, TypeScript, RxJS e Angular Material.
  - Arquivos/módulos: `frontend/package.json`, configuração do workspace Angular, `frontend/src/main.ts` e `frontend/src/app/`.
  - Verificação: o comando de instalação e o comando de build definidos para o projeto executam sem erro; não há segredo nem dependência de MinIO/RabbitMQ/PostgreSQL no frontend.

- [ ] T-002 [FR-001][FR-015] Criar o shell da aplicação e as rotas de upload e acompanhamento, usando a rota de feature em `frontend/src/app/document/`.
  - Arquivos/módulos: `frontend/src/app/app.routes.ts`, `frontend/src/app/document/`.
  - Verificação: a rota inicial carrega a tela de upload e a rota de acompanhamento aceita um `documentId` sem depender de estado em memória.

- [ ] T-003 [FR-016] Configurar URL base da API e proxy local sem credenciais ou endpoints de infraestrutura.
  - Arquivos/módulos: configuração de ambiente/proxy do frontend e documentação de execução local.
  - Verificação: uma chamada relativa ao backend funciona pelo proxy; busca textual não encontra tokens, senhas, `MINIO`, `RABBITMQ` ou conexões de banco no código do frontend.

- [ ] T-004 [FR-007][FR-008][FR-012] Definir os modelos TypeScript do contrato HTTP e os tipos de estado e erro da feature.
  - Arquivos/módulos: `frontend/src/app/document/`.
  - Verificação: os modelos cobrem `id`, `originalFilename`, `contentType`, `sizeBytes`, `status`, `createdAt`, `updatedAt`, `ProblemDetail` e os nove `errorCode` aprovados.

## Fase 2 — Lógica principal

- [ ] T-005 [FR-002][FR-003][FR-004] Implementar o validador local de arquivo para nome, tipo informado, conteúdo vazio e limite de `52.428.800` bytes.
  - Arquivos/módulos: serviço/utilitário da feature em `frontend/src/app/document/`.
  - Verificação: testes unitários cobrem arquivo válido, vazio, sem nome, sem tipo, no limite e acima do limite; o validador não tenta identificar assinatura nem cria whitelist adicional.

- [ ] T-006 [FR-004][FR-005][FR-006][FR-014] Implementar o serviço de upload usando `FormData` com uma única parte `file` e eventos de progresso.
  - Arquivos/módulos: serviço HTTP da feature em `frontend/src/app/document/`.
  - Verificação: teste com `HttpTestingController` confirma `POST /documents`, `multipart/form-data` gerado pelo navegador, ausência de metadata e emissão de progresso/sucesso/erro.

- [ ] T-007 [FR-007][FR-008][FR-010][FR-011][FR-013] Implementar o serviço de consulta por identificador e o mapeamento dos quatro estados públicos.
  - Arquivos/módulos: serviço HTTP e modelos em `frontend/src/app/document/`.
  - Verificação: testes confirmam `GET /documents/{documentId}`, preservação dos timestamps/metadados, tratamento distinto de `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` e `404`.

- [ ] T-008 [FR-009][FR-014] Implementar o polling automático de três segundos, atualização manual e parada em `COMPLETED`, `FAILED` ou `404`.
  - Arquivos/módulos: serviço/orquestrador RxJS da feature.
  - Verificação: testes com scheduler virtual comprovam consultas a cada três segundos enquanto pendente/processando, refresh manual e encerramento sem requisições adicionais após estado terminal.

- [ ] T-009 [FR-012][FR-013][FR-014] Implementar o mapeamento de `ProblemDetail`, erros de rede, timeout, `5xx`, respostas desconhecidas e `404`.
  - Arquivos/módulos: mapeador e tipos de erro em `frontend/src/app/document/`.
  - Verificação: cada `errorCode` recebe mensagem estável; código desconhecido e falha operacional recebem fallback; `404` não é convertido em `PENDING`.

- [ ] T-010 [FR-014] Impedir retry automático do upload quando a resposta do `POST` for perdida ou ambígua.
  - Arquivos/módulos: serviço e estado de envio da feature.
  - Verificação: teste simula erro após o envio e comprova que nenhuma segunda chamada é disparada sem ação explícita do usuário.

## Fase 3 — Interface

- [ ] T-011 [FR-001][FR-002][FR-003][FR-005] Criar o componente de seleção/arraste, revisão do arquivo e bloqueio de submissão inválida ou duplicada.
  - Arquivos/módulos: componente de upload em `frontend/src/app/document/` e estilos da feature.
  - Verificação: teste de componente comprova seleção, nome/tipo/tamanho visíveis, mensagens locais, botão bloqueado durante envio e uma única ação por arquivo.

- [ ] T-012 [FR-004][FR-005][FR-006] Integrar o componente de upload ao serviço HTTP e exibir progresso, sucesso `201` e estado inicial `PENDING`.
  - Arquivos/módulos: componente de upload e template Angular Material.
  - Verificação: teste de componente simula eventos HTTP, confirma progresso visível, navegação para o detalhe e exibição do identificador/metadados retornados.

- [ ] T-013 [FR-007][FR-008][FR-009][FR-015] Criar a tela de acompanhamento por `documentId`, carregável diretamente e após recarregar o navegador.
  - Arquivos/módulos: componente de detalhe, rota e estilos em `frontend/src/app/document/`.
  - Verificação: teste de rota simula acesso direto e confirma chamada ao `GET`, estados de loading e mensagem de consulta assíncrona.

- [ ] T-014 [FR-008][FR-010][FR-011] Criar a apresentação visual dos estados `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.
  - Arquivos/módulos: componente de status/timeline da feature.
  - Verificação: testes de componente confirmam rótulo e indicação visual distintos; `PENDING`/`PROCESSING` não mostram sucesso final; `FAILED` não oferece retry inexistente.

- [ ] T-015 [FR-009][FR-011][FR-013][FR-014] Adicionar atualização manual, novo upload, nova consulta, estados de erro operacional e documento não encontrado.
  - Arquivos/módulos: componentes de upload/detalhe e mapeador de mensagens.
  - Verificação: testes de interação confirmam cada ação, preservação do `documentId` quando aplicável e ausência de dados locais apresentados como estado atual após `404`.

- [ ] T-016 [FR-001][FR-002][FR-003][FR-008][FR-016] Revisar acessibilidade básica, responsividade e conteúdo visível da interface.
  - Arquivos/módulos: templates, estilos globais e componentes da feature.
  - Verificação: inspeção e teste automatizado verificam labels/estados acessíveis, foco no erro, operação sem depender apenas de cor e ausência de credenciais/infraestrutura no bundle.

## Fase 4 — Testes

- [ ] T-017 [FR-002][FR-003][FR-004][FR-012] Cobrir validação local e tradução dos nove códigos de erro.
  - Arquivos/módulos: testes unitários de `frontend/src/app/document/`.
  - Verificação: arquivo válido, limites, arquivo vazio, erros de tipo/assinatura e código desconhecido produzem as mensagens e bloqueios previstos.

- [ ] T-018 [FR-004][FR-005][FR-006][FR-007][FR-013][FR-014] Cobrir os serviços HTTP com `HttpTestingController`.
  - Arquivos/módulos: testes dos serviços de API e erros.
  - Verificação: os testes confirmam método, rota, parte `file`, progresso, resposta `201`, resposta de detalhe, `400`, `404`, `5xx`, timeout e rede.

- [ ] T-019 [FR-008][FR-009][FR-010][FR-011] Cobrir polling e máquina de apresentação de estados.
  - Arquivos/módulos: testes do poller, componentes de status e detalhe.
  - Verificação: polling de três segundos permanece em estados não terminais, para nos terminais/404 e não transforma `PROCESSING` em erro por demora.

- [ ] T-020 [FR-001][FR-002][FR-005][FR-006][FR-015] Cobrir o fluxo dos componentes com caminho feliz e erros prioritários.
  - Arquivos/módulos: testes de componentes e roteamento.
  - Verificação: upload válido chega ao detalhe; validação local bloqueia envio; `ProblemDetail`, `FAILED`, `404` e erro operacional são exibidos corretamente.

- [ ] T-021 [FR-001][FR-004][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016] Executar integração contra o backend local e regressão do backend.
  - Arquivos/módulos: configuração de execução/teste do frontend, `backend/` e infraestrutura local existente.
  - Verificação: AC-001 a AC-004 passam contra `POST /documents` e `GET /documents/{documentId}` reais; a suíte do backend permanece com 92 testes passando ou qualquer divergência é registrada antes de avançar.

## Fase 5 — Entrega

- [x] T-022 [FR-016] Validar build de produção e configuração de ambiente/proxy.
  - Arquivos/módulos: configuração Angular, ambientes, proxy e documentação de execução.
  - Verificação: build de produção passa; a URL da API é configurável; nenhum segredo, token de infraestrutura ou endpoint MinIO/RabbitMQ aparece no artefato.

- [x] T-023 [FR-001][FR-004][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011] Executar smoke test do fluxo completo com backend local.
  - Arquivos/módulos: frontend, backend e infraestrutura local já existente.
  - Verificação: selecionar arquivo → enviar → observar `PENDING` → acompanhar `PROCESSING` → observar `COMPLETED` ou `FAILED`; validar também arquivo incompatível e documento inexistente.

- [x] T-024 [FR-001][FR-016] Atualizar `docs/frontend.md` com a execução local e adicionar o link correspondente no `README.md`, registrando também o resultado da revisão de convergência.
  - Arquivos/módulos: `docs/frontend.md`, `README.md`, `specs/05-verificacao/`.
  - Verificação: outra pessoa consegue iniciar o frontend com Node `v24.19.0`/npm `12.0.2`, compreender o proxy e reproduzir o smoke test sem configuração secreta.

- [x] T-025 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015][FR-016] Revisar Spec, plano, tarefas, arquitetura, código e testes antes de qualquer deploy.
  - Arquivos/módulos: todos os arquivos da feature e checklist de convergência.
  - Verificação: cada FR e AC aponta para implementação e teste; nenhuma alteração de backend/migration foi feita fora do escopo; Marcos revisou e aprovou.

## Adiado (fora do escopo desta rodada)

- [ ] Criar listagem, busca, filtros, paginação ou histórico global de documentos.
- [ ] Criar download, preview ou streaming do conteúdo final.
- [ ] Criar autenticação, autorização ou multi-tenant.
- [ ] Criar retry manual, cancelamento, exclusão ou reprocessamento.
- [ ] Alterar o backend para expor CORS, SSE/WebSocket, progresso detalhado, motivo de falha ou novas APIs sem nova Spec.
- [ ] Criar uma ADR para CORS/BFF, eventos em tempo real ou contrato de diagnóstico; abrir somente se uma dessas decisões sair do escopo aprovado.
