# Tarefas — Busca, filtros, paginação, ordenação, download em lote e exclusão em lote

Plano relacionado: `specs/04-plano/listagem-download-exclusao-documentos/plano.md`

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente para revisão isolada, referenciar o requisito
`FR-XXX` que implementa e declarar como verificar que ficou pronta. A ordem abaixo é
obrigatória. Nenhuma tarefa de lógica ou interface deve começar antes do gate da Fase 1.

## Fase 1 — Base

- [ ] T-001 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Mapear a Spec aprovada contra o código, arquitetura, ADR-006, contratos e testes existentes, registrando o baseline da listagem, upload, detalhe, download individual e exclusão individual.
  - Arquivos/módulos: `specs/03-features/listagem-download-exclusao-documentos/spec.md`, `specs/02-arquitetura/`, `specs/02-arquitetura/DECISAO/ADR-006-exclusao-definitiva-documentos.md`, `backend/.../document/`, `frontend/src/app/document/` e testes existentes.
  - Verificação: matriz FR/AC → contrato/código/teste preenchida; confirma-se o comportamento atual de `GET /documents`, os sete campos, os quatro estados e a preservação dos fluxos existentes.

- [ ] T-002 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-013][FR-014] Fechar e documentar o contrato de `GET /documents` com query parameters para filename case-insensitive, filtros por `status`/`contentType`/data, filtros vazios ignorados, dez itens por página, metadados X/Y, todos os campos ordenáveis, validações e compatibilidade sem parâmetros.
  - Arquivos/módulos: documentação da API, `DocumentController`, DTOs web, `DocumentResponse`, `DocumentRepository` e modelos Angular.
  - Verificação: os nomes/tipos dos parâmetros, formato da resposta, data/timezone, direção padrão, desempate e erros inválidos estão aprovados e cobertos por exemplos de contrato.

- [ ] T-003 [FR-008][FR-012] Fechar e documentar o contrato do download em lote como uma única operação de arquivo compactado, definindo rota/verb, requisição de IDs, formato, nome, headers, streaming, itens não `COMPLETED`, itens ausentes e falhas.
  - Arquivos/módulos: contrato da API, DTOs web, `DocumentStorage`, documentação de download e modelos frontend.
  - Verificação: o contrato aprovado deixa claro que somente `COMPLETED` entra no ZIP e define como o cliente identifica falha total ou parcial sem receber um falso sucesso.

- [ ] T-004 [FR-009][FR-010][FR-011][FR-012][FR-015] Fechar e documentar o contrato da exclusão em lote como uma única requisição com vários IDs, incluindo rota/verb, confirmação frontend, resultado por item, sucesso parcial, mensagens padrão e status HTTP.
  - Arquivos/módulos: contrato da API, DTOs de requisição/resposta, `DocumentController`, `DocumentService` e modelos frontend.
  - Verificação: o contrato aprovado diferencia itens removidos, não removidos e inexistentes; cancelar a confirmação não gera requisição; os contratos individuais permanecem inalterados.

- [ ] T-005 [FR-010][FR-012][FR-015] Revisar ou estender o ADR-006 para cobrir exclusão em lote, documentos `PENDING`/`PROCESSING`, lock, staging, storage final, idempotência, mensagens RabbitMQ tardias, DLQ e sucesso parcial.
  - Arquivos/módulos: `specs/02-arquitetura/DECISAO/ADR-006-exclusao-definitiva-documentos.md` ou novo ADR no mesmo diretório, consumidor RabbitMQ, staging e serviço de documento.
  - Verificação: o ADR aprovado descreve a ordem das operações e o destino de cada falha; nenhuma corrida com consumidor/reconciliação permanece sem comportamento definido; se exigir schema/mensagem/estado novo, a implementação fica bloqueada até nova aprovação.

- [ ] T-006 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Preparar fixtures e testes de contrato para quatro estados, nomes com variação de caixa, filtros, datas, paginação, ordenação, seleção persistente, ZIP e falhas parciais.
  - Arquivos/módulos: `backend/src/test/java/.../document/`, `frontend/src/app/document/*.spec.ts`, fixtures de integração e `specs/05-verificacao/`.
  - Verificação: o baseline existente passa antes da lógica nova; os fixtures distinguem documentos `COMPLETED` dos demais e permitem verificar cada item de um resultado parcial.

**Gate da Fase 1:** T-001 a T-006 revisadas; contratos e perguntas abertas da Spec
registrados; ADR-006 aprovado para o lote; nenhuma decisão de rota, status, ZIP,
ordenação ou data ficou implícita.

## Fase 2 — Lógica principal

- [ ] T-007 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-013][FR-014] Implementar a consulta paginada no repositório/adaptador de persistência, usando dez itens por página, filename case-insensitive, filtros AND, filtros vazios ignorados e whitelist de todos os campos exibidos para ordenação.
  - Arquivos/módulos: `backend/src/main/java/com/dockflow/dockflow/document/DocumentRepository.java` e o adaptador de persistência existente; criar apenas componentes internos ao domínio `document` quando necessário.
  - Verificação: testes de repositório confirmam quantidade, critérios combinados, lista vazia, todos os campos ordenáveis e ausência de montagem de SQL com campo livre recebido do cliente.

- [ ] T-008 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-013][FR-014] Implementar no serviço e no controller o mapeamento dos query parameters de `GET /documents`, a resposta paginada, metadados de página X/Y, valores padrão e validação determinística.
  - Arquivos/módulos: `DocumentService.java`, `DocumentController.java`, DTOs/mappers existentes e exceções web; manter os pacotes atuais sem reorganização gratuita.
  - Verificação: testes de serviço/controller confirmam critérios combinados, dez itens, metadados, resposta sem critérios e erros para parâmetros inválidos; `GET /documents/{documentId}` continua resolvendo corretamente.

- [ ] T-009 [FR-008][FR-012] Implementar o caso de uso de download em lote com streaming incremental do arquivo compactado, abrindo somente conteúdos `COMPLETED`, fechando cada stream e sem carregar o conjunto em memória.
  - Arquivos/módulos: `DocumentService`, aplicação de download, `DocumentStorage`/porta equivalente, adaptador MinIO, controller e headers HTTP.
  - Verificação: teste produz ZIP legível com bytes corretos, fecha streams em sucesso/erro, não expõe `objectKey` e não usa `byte[]` como contrato obrigatório do lote.

- [ ] T-010 [FR-008][FR-012] Implementar a validação e o resultado do download em lote para documentos `PENDING`, `PROCESSING`, `FAILED`, ausentes e indisponíveis no storage, conforme contrato aprovado.
  - Arquivos/módulos: `DocumentService`, exceções do domínio, `GlobalExceptionHandler`/adaptador web e mapeador de erros frontend.
  - Verificação: nenhum documento não `COMPLETED` aparece como incluído com sucesso; os testes diferenciam falha total, falha por item e resposta compactada válida conforme o contrato.

- [ ] T-011 [FR-009][FR-010][FR-011][FR-012][FR-015] Implementar a exclusão em lote como uma única entrada HTTP, delegando cada documento à política do ADR-006 e produzindo resultado por item para sucesso total, parcial, inexistente e falha de storage.
  - Arquivos/módulos: `DocumentService`, `DocumentRepository`, `DocumentStorage`, `DocumentStagingRemover`, controller, exceções e adaptadores existentes.
  - Verificação: testes demonstram lock antes da remoção, final/staging idempotentes, registro preservado quando necessário, itens não removidos reportados e nenhum sucesso antes da confirmação correspondente.

- [ ] T-012 [FR-010][FR-012][FR-015] Garantir que mensagens RabbitMQ tardias e corridas com consumidor/reconciliação sigam o ADR-006, sem recriar documento excluído, armazenar conteúdo ou executar retry de negócio indevido.
  - Arquivos/módulos: consumidor RabbitMQ existente, DLQ, claim/reconciliação, testes de concorrência e integração.
  - Verificação: teste com mensagem publicada antes da exclusão e consumida depois confirma documento ausente tratado como inválido/DLQ, sem novo registro nem objeto final.

- [ ] T-013 [FR-012][FR-015] Mapear erros de consulta, ZIP, exclusão parcial, storage indisponível e documento inexistente sem alterar silenciosamente os contratos individuais.
  - Arquivos/módulos: exceções/adaptadores web do domínio `document`, `GlobalExceptionHandler` e testes de controller.
  - Verificação: respostas de erro e mensagens padrão coincidem com o contrato; upload, detalhe, download individual e exclusão individual continuam passando.

- [ ] T-014 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012] Atualizar o `DocumentApiService` e os modelos frontend para query parameters, página, seleção, download compactado e resultado de exclusão parcial.
  - Arquivos/módulos: `frontend/src/app/document/document-api.service.ts`, `document.models.ts`, `document-error.mapper.ts` e specs HTTP.
  - Verificação: `HttpTestingController` confirma `GET /documents` com query parameters, método/rota do ZIP e requisição única de exclusão, sem acesso direto a MinIO, RabbitMQ ou PostgreSQL.

**Gate da Fase 2:** consulta server-side, ZIP streaming, exclusão parcial e tratamento
de estados estão testados; contratos individuais e os testes de baseline permanecem
verdes.

## Fase 3 — Interface

- [ ] T-015 [FR-001][FR-002][FR-005][FR-006][FR-013] Adicionar busca por filename, filtros de status/contentType/data e estado de carregamento/erro/vazio na tela de documentos.
  - Arquivos/módulos: `frontend/src/app/document/pages/document-list-page.*`, modelos e serviço de API.
  - Verificação: teste de componente confirma busca case-insensitive pelo contrato, filtros AND, filtros vazios ignorados, lista vazia e mensagem padrão de erro.

- [ ] T-016 [FR-003][FR-004][FR-005][FR-006][FR-013][FR-014] Adicionar controles de ordenação por todos os campos e paginação de dez itens com indicador X/Y.
  - Arquivos/módulos: página de listagem, template, estilos, estado de consulta e rotas existentes.
  - Verificação: teste de componente confirma mudança de sort/page, chamada com os parâmetros atuais, dez itens por página, indicador correto e ausência de mistura entre respostas.

- [ ] T-017 [FR-007][FR-008][FR-009][FR-010][FR-011] Implementar seleção individual/múltipla persistente entre página, busca, filtro e ordenação, sem limite máximo de itens selecionados.
  - Arquivos/módulos: estado da página de listagem, template da tabela, modelos de seleção e testes de componente.
  - Verificação: selecionar itens, trocar consulta e retornar confirma que os IDs continuam selecionados; nenhuma ação é habilitada sem seleção; nenhum conteúdo binário é mantido no estado da interface.

- [ ] T-018 [FR-008][FR-012] Integrar download em lote para iniciar uma única operação, receber o ZIP e tratar falhas com mensagens padrão, sem iniciar exclusão.
  - Arquivos/módulos: página de listagem, `DocumentApiService`, utilitário de download e mapeador de erros.
  - Verificação: teste confirma uma chamada por ação, download do arquivo compactado, revogação de URL temporária quando aplicável e erro visível para lote não concluído.

- [ ] T-019 [FR-009][FR-010][FR-011][FR-012] Integrar confirmação explícita, cancelamento sem HTTP, exclusão única em lote, remoção local somente de itens confirmados e mensagens individuais de sucesso parcial.
  - Arquivos/módulos: página de listagem, diálogo de confirmação, `DocumentApiService`, estado de seleção e mapeador de erros.
  - Verificação: cancelar não chama API; confirmar envia uma única requisição; sucesso remove itens confirmados; itens falhos permanecem selecionados/visíveis conforme contrato e recebem mensagem padrão.

- [ ] T-020 [FR-006][FR-007][FR-015] Revisar acessibilidade, foco, teclado, responsividade, estados concorrentes e coexistência de `documents`, `documents/new` e `documents/:documentId`.
  - Arquivos/módulos: rotas, templates, estilos e testes existentes do frontend.
  - Verificação: navegação por teclado e foco de confirmação/erro são verificáveis; upload, detalhe e ações individuais continuam navegáveis e funcionais.

**Gate da Fase 3:** interface cobre consulta combinada, X/Y, seleção persistente,
download ZIP, confirmação/cancelamento, sucesso parcial e regressão de navegação.

## Fase 4 — Testes

- [ ] T-021 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-013][FR-014] Cobrir unitariamente repositório, serviço, mapper e controller para busca, filtros, data, paginação, ordenação, lista vazia e parâmetros inválidos.
  - Arquivos/módulos: testes do repositório, `DocumentService`, `DocumentController`, DTOs e exceções web.
  - Verificação: testes cobrem case-insensitive, AND, vazios ignorados, dez por página, X/Y, todos os campos ordenáveis, desempate e erro determinístico.

- [ ] T-022 [FR-008][FR-012] Cobrir download em lote com ZIP, conteúdo, headers, somente `COMPLETED`, falha por estado, item ausente, storage indisponível e fechamento de streams.
  - Arquivos/módulos: testes de serviço, `DocumentStorage`, adaptador MinIO, controller e integração PostgreSQL/MinIO.
  - Verificação: ZIP é aberto e comparado com o conteúdo esperado; itens proibidos não entram silenciosamente; nenhuma credencial ou `objectKey` aparece na resposta.

- [ ] T-023 [FR-009][FR-010][FR-011][FR-012][FR-015] Cobrir exclusão em lote total/parcial para `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`, incluindo falha de storage, documento ausente, repetição e concorrência.
  - Arquivos/módulos: `DocumentService`, repositório, storage, staging, exceções e integração.
  - Verificação: cada item tem resultado verificável; itens falhos permanecem; remoções são definitivas/idempotentes; nenhuma falha é reportada como sucesso total.

- [ ] T-024 [FR-010][FR-012][FR-015] Cobrir a corrida entre exclusão, consumidor RabbitMQ e reconciliação conforme ADR-006.
  - Arquivos/módulos: consumidor, DLQ, scheduler/reconciliação e testes de integração com RabbitMQ.
  - Verificação: mensagem tardia para documento removido vai para o destino definido, sem recriação, storage final ou retry indevido; lock e claim não permitem processamento concorrente inválido.

- [ ] T-025 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012] Cobrir o `DocumentApiService` com `HttpTestingController` para query parameters, metadados, ZIP, exclusão única em lote, seleção vazia e sucesso parcial.
  - Arquivos/módulos: specs do serviço HTTP, modelos e mapeadores frontend.
  - Verificação: rotas, verbos, corpos, headers, response type, erros e ausência de chamadas de infraestrutura direta coincidem com os contratos aprovados.

- [ ] T-026 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-015] Cobrir os componentes Angular com caminho feliz, erro de consulta, lista vazia, paginação, seleção persistente, ZIP, confirmação/cancelamento, falha parcial e regressão dos fluxos individuais.
  - Arquivos/módulos: specs da página de listagem, confirmação, seleção, paginação e utilitário de download.
  - Verificação: AC-001 a AC-010 são exercitados; cancelamento não chama API, seleção sobrevive à consulta, itens falhos não desaparecem e upload/detalhe continuam verdes.

- [ ] T-027 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Executar integração ponta a ponta contra backend, PostgreSQL, MinIO, RabbitMQ e frontend, seguida da regressão completa.
  - Arquivos/módulos: frontend, backend, Testcontainers/Compose, `specs/05-verificacao/` e checklist de convergência.
  - Verificação: consulta combinada, página X/Y, ZIP somente `COMPLETED`, exclusão total/parcial em todos os estados e todos os fluxos existentes passam com evidência registrada.

**Gate da Fase 4:** AC-001 a AC-010 passam; falhas parciais são verificadas por item;
integração e regressão permanecem verdes; nenhuma divergência entre Spec, contrato,
código e teste fica sem registro.

## Fase 5 — Entrega

- [ ] T-028 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014] Atualizar documentação da API e frontend com query parameters, resposta X/Y, ZIP, estados permitidos, sucesso parcial, mensagens padrão e exemplos.
  - Arquivos/módulos: `docs/`, README, contrato da API, ADR-006, documentação da feature e `specs/05-verificacao/`.
  - Verificação: outra pessoa consegue reproduzir consulta, seleção, download ZIP, exclusão parcial e cancelamento; documentos coincidem com controller, frontend e testes.

- [ ] T-029 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Executar build, suíte prioritária e inspeção de segurança do backend/frontend.
  - Arquivos/módulos: `backend/`, `frontend/`, configuração de ambiente/proxy e artefatos de build.
  - Verificação: builds e testes passam; não há credenciais, `objectKey`, endpoints MinIO/RabbitMQ ou conexões de banco no frontend; não foi criada migration sem justificativa aprovada.

- [ ] T-030 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Executar smoke test local: filename case-insensitive, filtros, consulta combinada, X/Y, ordenação, seleção persistente, ZIP, seleção vazia, exclusão total/parcial e documentos em todos os estados.
  - Arquivos/módulos: ambiente local, PostgreSQL, MinIO, RabbitMQ, backend, frontend e checklist de verificação.
  - Verificação: cada cenário tem evidência; falhas não são apresentadas como sucesso; upload, detalhe, download individual e exclusão individual continuam funcionando.

- [ ] T-031 [FR-001][FR-002][FR-003][FR-004][FR-005][FR-006][FR-007][FR-008][FR-009][FR-010][FR-011][FR-012][FR-013][FR-014][FR-015] Executar revisão final de convergência e obter aprovação de Marcos antes de qualquer deploy.
  - Arquivos/módulos: Spec, plano, tarefas, código, testes, ADR, documentação, checklist e procedimento de rollback.
  - Verificação: cada FR/AC aponta para implementação e teste; riscos e decisões estão registrados; rollback está documentado; Marcos aprovou; nenhum deploy é executado nesta tarefa.

## Adiado (fora do escopo desta rodada)

- [ ] Preview ou visualização inline do conteúdo, suporte a `Range` ou retomada de download.
- [ ] Busca textual avançada, relevância, OCR ou indexação externa.
- [ ] Autenticação, autorização, multi-tenant, CORS ou BFF.
- [ ] Retry automático de download/exclusão ou reprocessamento de documentos.
- [ ] Limpeza automática genérica de objetos órfãos fora da política de exclusão aprovada.
- [ ] Alteração dos fluxos existentes de upload, consulta por identificador e operações individuais.
- [ ] Deploy ou hospedagem externa sem aprovação final.
