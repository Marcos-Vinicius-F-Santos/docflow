# Baseline e matriz de contrato — Listagem, busca e operações em lote

Status: Preparado para T-001 a T-006 — não representa aprovação da implementação  
Feature: `listagem-download-exclusao-documentos`  
Plano: `specs/04-plano/listagem-download-exclusao-documentos/plano.md`

## 1. Baseline observado

| Fluxo | Contrato atual | Código atual | Teste atual | Situação antes da Fase 2 |
|---|---|---|---|---|
| Registro | `POST /documents` multipart com `file` | `DocumentController.registerDocument`, `DocumentService.registerDocument` | `DocumentControllerTest`, testes de registro e integração | Deve permanecer verde |
| Consulta por ID | `GET /documents/{documentId}` | `DocumentController.findDocumentById`, `DocumentService.findById` | `DocumentControllerTest` | Deve permanecer verde |
| Listagem | `GET /documents` sem critérios; retorna lista simples | `DocumentController.findDocuments`, `DocumentService.findAll` | `DocumentControllerTest`, `DocumentServiceTest` | Será ampliada em T-007/T-008 |
| Download individual | `GET /documents/{documentId}/content` somente `COMPLETED`, streaming | `DocumentController.downloadDocument`, `DocumentService.download`, `DocumentStorage` | testes de controller, serviço e storage | Deve permanecer verde |
| Exclusão individual | `DELETE /documents/{documentId}` após confirmação no frontend; `204` | `DocumentController.deleteDocument`, `DocumentService.delete` | testes de controller/serviço/integração | Deve permanecer verde |
| Frontend de documentos | lista completa em memória, download e exclusão individual | `DocumentListPage`, `DocumentApiService` | `document-pages.spec.ts`, `document-api.service.spec.ts` | Será ampliado somente após o gate da Fase 1 |

## 2. Matriz FR/AC → contrato → código/teste

| Requisito/aceite | Contrato ou evidência esperada | Baseline atual | Preparação T-001/T-006 | Implementação posterior |
|---|---|---|---|---|
| FR-001 / AC-001 | `GET /documents?search=...` com `contains` case-insensitive em `originalFilename` | Não existe query parameter | Fixture com nomes `Report.pdf`, `report-2.pdf` e `notes.txt` | T-007/T-008 |
| FR-002 / AC-001 | `status`, `contentType`, `createdFrom/to`, `updatedFrom/to` | Não existem filtros | Fixture dos quatro estados, MIME types e instants UTC | T-007/T-008 |
| FR-003 / AC-002 | `page` 1-based, dez itens, `content/page/size/totalElements/totalPages` | Lista simples sem paginação | Fixture com mais de dez documentos e página vazia | T-007/T-008 |
| FR-004 / AC-001 | `sort=campo,direcao`, padrão `updatedAt,desc`, `createdAt,desc`, `id,asc` | Ordem atual não é contrato | Fixture com empates de datas e todos os campos permitidos | T-007/T-008 |
| FR-005 / AC-001 | Critérios preenchidos aplicados no backend por AND | Não existe consulta combinada | Caso de contrato com busca + status + MIME + data + página | T-007/T-008 |
| FR-006 | Cada item preserva os sete campos do `DocumentResponse` | Já existe em `DocumentResponse`/`DocumentMapper` | Fixture JSON dos sete campos | Preservado por T-008/T-014 |
| FR-007 | Seleção de IDs persiste entre consultas | Não existe seleção em lote na tela atual | Fixture de seleção com itens de páginas diferentes | T-017 |
| FR-008 / AC-003/008 | `POST /documents/batch-download`, ZIP, somente `COMPLETED`, header de ignorados | Não existe endpoint | Fixture com `COMPLETED` e estados ignorados | T-009/T-010/T-018 |
| FR-009 / AC-005 | Confirmação antes de `DELETE /documents` | Confirmação existe somente para item individual | Fixture de seleção e confirmação cancelada | T-019 |
| FR-010 / AC-004 | `DELETE /documents` único com `results` por item | Não existe endpoint | Fixture de sucesso total e parcial | T-011/T-019 |
| FR-011 / AC-005 | Cancelamento sem requisição | Já coberto para exclusão individual | Caso de contrato sem chamada ao backend | T-019 |
| FR-012 / AC-007/008/009 | `ProblemDetail` para falha total; erro/resultados por item | `ProblemDetail` já existe para contratos atuais | Fixtures de `400`, `409`, `503`, `500` e resultado parcial | T-010/T-013/T-023 |
| FR-013 / AC-006 | `content: []`, `totalElements: 0`, `totalPages: 0` | Atual retorna `[]` | Fixture de consulta sem resultado | T-008/T-015 |
| FR-014 | `GET /documents` sem critérios continua acessível | Existe lista simples atual | Teste de regressão sem parâmetros | T-008/T-021 |
| FR-015 / AC-010 | Upload, consulta por ID, operações individuais sem regressão | Existem contratos e testes atuais | Baseline de regressão registrado acima | T-006/T-027 |

## 3. Critérios de regressão obrigatórios

- `POST /documents` continua aceitando somente a parte `file` e mantendo `201 Created`,
  `Location` e `DocumentResponse`.
- `GET /documents/{documentId}` continua retornando o contrato individual existente e
  `404` para documento ausente.
- `GET /documents/{documentId}/content` continua aceitando somente conteúdo
  `COMPLETED`, com streaming, headers aprovados e códigos atuais.
- `DELETE /documents/{documentId}` continua exigindo confirmação no frontend, retorna
  `204` em sucesso e preserva o ADR-006.
- Nenhuma camada frontend acessa PostgreSQL, RabbitMQ, MinIO, bucket ou `objectKey`.
- Nenhuma migration é criada durante T-001 a T-006.

## 4. Limite desta preparação

Este baseline e suas fixtures documentam contratos aprovados e casos que deverão ser
exercitados. Eles não implementam consulta paginada, geração de ZIP, exclusão em lote,
seleção persistente ou novos DTOs de produção. Essas mudanças pertencem às tarefas
posteriores ao gate da Fase 1.
