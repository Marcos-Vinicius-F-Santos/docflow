# Matriz de baseline e rastreabilidade — Hub de documentos

Data do baseline: 2026-10-27  
Feature: `hub`  
Escopo: T-001 e preparação para T-006

Esta matriz compara a Spec da feature com o comportamento existente antes da lógica
nova de retry, contagem de falhas e exclusão atrasada. As lacunas identificadas são
intencionais nesta etapa; elas serão implementadas somente nas tarefas correspondentes
do plano.

## 1. Baseline operacional

| Área | Comportamento atual | Evidência | Situação para o Hub |
|---|---|---|---|
| `/` | Redireciona para `/documents/new`. | `frontend/src/app/app.routes.ts` | Lacuna de FR-001; será alterada em T-016. |
| Upload | `DocumentUploadPage` valida o arquivo localmente e usa `DocumentApiService.upload`, enviando somente a parte multipart `file`. | `frontend/src/app/document/pages/document-upload-page.ts`, `document-api.service.ts`, `document-pages.spec.ts`, `document-api.service.spec.ts` | Baseline preservado; será integrado ao Hub em T-018. |
| Acompanhamento | A rota `/documents/:documentId` consulta o documento e usa polling para `PENDING`/`PROCESSING`. | `document-detail-page.ts`, `document-status-poller.service.ts`, specs correspondentes | Baseline preservado; cobre parte de FR-016. |
| Listagem | `GET /documents` é consumido como `DocumentResponse[]` e a página lista os quatro estados públicos. | `DocumentController.findDocuments`, `document-api.service.ts`, `document-pages.spec.ts` | Baseline preservado; será reutilizado em T-017/T-019. |
| Download | Documentos concluídos usam `GET /documents/{documentId}/content` e download individual. | `document-api.service.ts`, `document-list-page.ts`, `document-api.service.spec.ts`, `document-pages.spec.ts` | Baseline preservado; será mantido junto ao item em T-021. |
| Exclusão individual | A página exige confirmação e chama `DELETE /documents/{documentId}`. | `document-list-page.ts`, `document-pages.spec.ts`, `DocumentController` | Baseline preservado; retry/exclusão atrasada não substituem este fluxo. |
| Retry de processamento | Não existe endpoint nem ação de retry para `FAILED`. | `DocumentController`, `DocumentService`, `DocumentApiService` | Lacunas FR-009 a FR-015; contrato será fechado em T-003 e lógica a partir de T-007. |
| Falhas persistidas | `Document` não possui `failure_count`, `is_excluded` ou `exclusion_scheduled_at`. | `Document.java`, migrations V1–V5 | Lacunas FR-010/FR-011/FR-013; migration será preparada em T-005. |
| Fila de exclusão | Não existe topologia ou consumidor para exclusão atrasada de três horas. | `document/messaging/rabbitmq/` atual | Lacunas FR-013/FR-014; contrato será fechado em T-004. |

### Divergência pré-existente registrada

`docs/api.md` descreve atualmente `GET /documents` como paginado, mas o controller e o
cliente Angular atuais usam uma coleção simples `DocumentResponse[]`. A Spec do Hub
define todos os documentos retornados pelo endpoint atual e não autoriza introduzir
paginação nesta feature. Portanto, esta divergência fica registrada para não ser
silenciosamente ampliada; nenhum comportamento foi alterado nesta fase.

## 2. Matriz FR → contrato → código → teste → lacuna

| Requisito | Contrato observado | Código existente | Teste existente | Lacuna / próxima tarefa |
|---|---|---|---|---|
| FR-001 | `/` atualmente redireciona. | `app.routes.ts` | Não há teste do destino do `/`. | Criar Hub e mudar rota em T-016. |
| FR-002 | Não há contrato visual de header do Hub. | Não há `hub/`. | Não aplicável. | Header DocFlow em T-016. |
| FR-003 | Não há faixa lateral do Hub. | Não há `hub/`. | Não aplicável. | Últimos cinco e todos `FAILED` em T-017. |
| FR-004 | Upload existente aceita seleção/arraste na tela antiga. | `DocumentUploadPage`, `DocumentFileValidator`. | `document-pages.spec.ts`, `document-validation.spec.ts`. | Reutilizar no dropbox do Hub em T-018. |
| FR-005 | `POST /documents` multipart com parte `file`. | `DocumentApiService.upload`, `DocumentController.registerDocument`. | `document-api.service.spec.ts`, `DocumentControllerTest`. | Reutilizar sem infraestrutura no frontend; T-018. |
| FR-006 | `GET /documents` retorna a coleção atual. | `DocumentController.findDocuments`, `DocumentListPage`. | Listagem e fixtures de estados. | Exibir a coleção no Hub em T-019. |
| FR-007 | Download individual de `COMPLETED`. | `DocumentApiService.download`, `DocumentListPage`. | Serviço e página cobertos. | Indicador/ação no Hub em T-020/T-021. |
| FR-008 | Polling distingue `PENDING`/`PROCESSING` de terminais. | `DocumentStatusPollerService`, `DocumentDetailPage`. | `document-status-poller.service.spec.ts`, `document-pages.spec.ts`. | Indicador refresh no Hub em T-020. |
| FR-009 | Não há `POST /documents/{id}/retry`. | Nenhum caso de uso ou ação. | Não há teste de retry. | Backend e ação visível em T-010/T-011/T-021. |
| FR-010 | Não há contador de falhas persistido. | `Document`/migrations V1–V5. | Não há teste de contador. | Domínio/consumidor/migration em T-007/T-009/T-012. |
| FR-011 | Sucesso atual marca `COMPLETED`, sem contador novo. | `Document.markCompleted`. | Testes do consumidor/storage. | Reset de falhas em T-007/T-012. |
| FR-012 | Não há reprocessamento do staging por comando HTTP. | Não há publisher de retry. | Não há teste. | Caso de uso com lock e staging em T-010. |
| FR-013 | Não há marcação ou fila de exclusão atrasada. | Não há campos/topologia/consumer. | Não há teste. | V6, publisher e consumer em T-005/T-013/T-014. |
| FR-014 | Exclusão individual já existe, sem marcação operacional. | `DocumentService.delete`, controller e página. | `document-pages.spec.ts` e testes backend. | Mensagem tardia/idempotência em T-014; manual preservada. |
| FR-015 | Nenhum contrato de retry no cliente. | `DocumentApiService` sem método. | Não há teste. | Contrato HTTP em T-003 e cliente em T-015. |
| FR-016 | Rotas de documentos existem. | `document.routes.ts`. | Teste de rotas/páginas existente. | Manter deep links em T-016/T-022. |
| FR-017 | Não há autenticação no frontend/API atual. | Sem guard ou associação a usuário. | Não aplicável ao baseline. | Preservar contexto único; não criar autenticação. |

## 3. Matriz BR/AC → evidência de baseline

| Grupo | O que o baseline confirma | O que ainda falta |
|---|---|---|
| BR-001/BR-005/BR-007 | O frontend usa API REST, valida upload localmente e não acessa serviços de infraestrutura. | Revalidar após o Hub e no teste de segurança T-031. |
| BR-002/BR-003/BR-004 | Os modelos e telas atuais distinguem os quatro estados; download está vinculado ao fluxo individual. | Indicadores visuais específicos do Hub em T-020. |
| BR-006/BR-011 a BR-018 | Nenhum comportamento novo de retry/exclusão atrasada existe no baseline. | Implementar backend, topologia, ações e concorrência nas tarefas da Fase 2/3. |
| AC-001 | A aplicação possui listagem e upload, mas `/` ainda abre a tela antiga. | Hub completo em T-016–T-022. |
| AC-002 | Upload multipart e validação existentes estão cobertos. | Integrar o dropbox e permanecer no Hub em T-018. |
| AC-003 | Todos os estados públicos já aparecem na listagem/fixtures. | Ícones e semântica visual em T-020. |
| AC-004 a AC-007 | Não implementados no baseline. | Retry, limite, fila e exclusão em T-007–T-015 e testes T-023–T-026. |
| AC-008 | A listagem possui mensagem de erro e tentativa manual. | Reutilizar no estado de erro do Hub em T-019. |
| AC-009 | Rotas existentes estão presentes e possuem cobertura de páginas. | Revalidar após mudança de `/` em T-016/T-028/T-029. |

## 4. Decisões para a sequência

- O baseline não altera comportamento existente.
- A divergência de paginação descrita acima não será resolvida por inferência nesta
  feature; qualquer mudança de contrato de `GET /documents` exige tarefa/spec própria.
- Retry, contador, migration, fila e exclusão atrasada permanecem lacunas até as tarefas
  explicitamente correspondentes.
