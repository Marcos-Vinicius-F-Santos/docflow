# Checklist de Convergência — Frontend Angular para o fluxo de documentos

Data da revisão: 2026-09-19

Status: revisão técnica concluída; aprovação final de Marcos pendente; nenhum deploy
externo executado.

## 1. Fontes verificadas

- [x] Princípios em `specs/00-principios/principios.md`.
- [x] Produto em `specs/01-produto/`.
- [x] Arquitetura e ADRs em `specs/02-arquitetura/`.
- [x] Spec em `specs/03-features/frontend-angular-docflow/spec.md`.
- [x] Plano e tarefas em `specs/04-plano/frontend-angular-docflow/`.

## 2. Rastreabilidade Spec ↔ Plano/Tarefas ↔ Código ↔ Testes

| Requisito       | Implementação principal                                   | Evidência                                                                                               |
| --------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| FR-001 a FR-003 | Tela de upload, seleção/arraste e validador local         | `document-upload-page.*`, `document-file-validator.ts`, `document-validation.spec.ts`                   |
| FR-004 a FR-006 | `FormData` com `file`, progresso e navegação após `201`   | `document-api.service.ts`, `document-pages.spec.ts`, `document-api.service.spec.ts`                     |
| FR-007 a FR-011 | Consulta, polling de 3 segundos e estados públicos        | `document-status-poller.service.ts`, `document-detail-page.*`, `document-status-poller.service.spec.ts` |
| FR-012 a FR-014 | Mapeamento de `ProblemDetail`, `404` e erros operacionais | `document-error.mapper.ts`, `document-validation.spec.ts`, `document-pages.spec.ts`                     |
| FR-015          | Rota direta `/documents/{documentId}`                     | `app.routes.ts`, `document.routes.ts`, `document-pages.spec.ts`                                         |
| FR-016          | Ambiente e proxy sem segredos ou infraestrutura exposta   | `environment*.ts`, `proxy.conf.json`, build e varredura do bundle                                       |

## 3. Critérios de aceite

- [x] AC-001: upload real com somente `file`, resposta `201`, consulta real e estado
      final `COMPLETED` confirmados contra o backend local.
- [x] AC-002: validação local bloqueia arquivo vazio e o caminho HTTP de erro é
      apresentado sem submissão inválida.
- [x] AC-003: polling de `PENDING`/`PROCESSING` e parada em estados terminais estão
      cobertos por testes com timers virtuais e pelo fluxo visual local.
- [x] AC-004: documento inexistente retorna `404`, preserva o identificador e não
      apresenta um estado local como se fosse o estado atual do backend.

## 4. Evidências de execução

Frontend:

```text
npm test -- --watch=false
5 test files passed
36 tests passed

npm run build
Application bundle generation complete

npx prettier --check ...
All matched files use Prettier code style
```

Backend:

```text
mvn test
Tests run: 92, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Smoke HTTP real contra `http://localhost:8080`:

```text
health: 200
POST /documents: 201
GET /documents/{id}: 200, status COMPLETED
GET /documents/{missing-id}: 404
```

Smoke visual com o navegador local:

- [x] `/documents/new` carregou com conteúdo significativo, controles acessíveis e
      botão de envio inicialmente bloqueado.
- [x] A rota direta `/documents/{documentId}` exibiu metadados e `COMPLETED` após o
      upload HTTP real.
- [x] A rota direta para um identificador inexistente exibiu `Documento não encontrado.`
      e preservou o identificador.
- [x] Com o backend temporariamente indisponível, a tela exibiu a falha operacional e
      a ação `Tentar consulta novamente`.
- [x] Teste manual aprovado por Marcos: PDF de 31,1 MiB enviado pela interface,
      exibido como `application/pdf` e concluído em `COMPLETED`.

## 5. Limites e divergências

- O frontend usa `/api/documents` em desenvolvimento e o proxy reescreve para
  `/documents`; o contrato do backend não foi alterado.
- O build de produção usa a base `/documents`, assumindo hospedagem sob a mesma origem
  da API ou uma borda de encaminhamento já existente. CORS/BFF não foram criados.
- Não houve alteração em backend, migration, PostgreSQL, RabbitMQ ou MinIO.
- O smoke HTTP criou um documento local de teste e o backend o processou com sucesso.
- O rollback consiste em remover/reverter somente o artefato do frontend; dados e
  infraestrutura do backend não fazem parte do rollback.

## 6. Resultado

A implementação está convergente com os FRs e ACs da Spec dentro do escopo aprovado.
T-022, T-023, T-024 e T-025 foram aprovadas por Marcos. Este checklist não aprova
deploy.
