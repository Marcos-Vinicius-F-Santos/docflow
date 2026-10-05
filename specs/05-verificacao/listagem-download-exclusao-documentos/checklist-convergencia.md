# Checklist de Convergência — Listagem, download e exclusão de documentos

Data da revisão: 2026-09-21  
Status: revisão concluída; deploy NÃO aprovado por esta revisão.

Legenda: `[x]` confirmado; `[~]` parcialmente confirmado ou com ressalva; `[ ]` não confirmado.

## 1. Escopo e fontes verificadas

- [x] Princípios em `specs/00-principios/principios.md`.
- [x] Spec aprovada em `specs/03-features/listagem-download-exclusao-documentos/spec.md`.
- [x] Arquitetura e ADRs em `specs/02-arquitetura/`.
- [x] Plano e tarefas em `specs/04-plano/listagem-download-exclusao-documentos/`.
- [x] Código atual do backend em `backend/src/main/java/.../document/`.
- [x] Código atual do frontend em `frontend/src/app/document/`.
- [x] Testes unitários, de controller, componente, integração e E2E relacionados à feature.

Observação sobre o caminho solicitado: `specs/05-verificacao/checklist-convergencia.md`
existe, mas é o checklist da feature `registro-binario-e-armazenamento-assincrono-documentos`.
Para não misturar features, este checklist foi preenchido no diretório específico da feature:
`specs/05-verificacao/listagem-download-exclusao-documentos/checklist-convergencia.md`.

## 2. Baseline e evidências executadas

| Verificação | Resultado |
|---|---|
| Frontend — `npm test -- --watch=false` em `frontend/` | [x] 5 arquivos, 58 testes passando |
| Backend — `mvn test` em `backend/` | [x] 115 testes passando |
| Build frontend — `npm run build` | [x] passou |
| Build backend — `mvn package -DskipTests` | [x] passou após repetição com acesso de rede autorizado; a primeira tentativa foi bloqueada pelo sandbox ao resolver o Maven Central |
| Integração com PostgreSQL, RabbitMQ e MinIO | [x] Testcontainers passaram |
| Smoke automatizado | [x] dois cenários E2E passaram, cobrindo listagem, download concluído, rejeição de download não concluído e exclusão |
| Inspeção de segurança do frontend | [x] não foram encontradas referências a `objectKey`, MinIO, RabbitMQ, PostgreSQL ou segredos em `frontend/src` |
| Smoke manual no navegador | [ ] não executado nesta revisão |

## 3. Rastreabilidade Spec → Plano/Tarefas → Código → Testes

| Requisito | Tarefas | Resultado da revisão | Evidência |
|---|---|---|---|
| FR-001 — listar documentos | T-016, T-019, T-020, T-021 | [x] confirmado | `DocumentService`, controller, `DocumentApiService`, página Angular, testes de serviço/componente e E2E |
| FR-002 — exibir os sete campos e todos os status | T-016, T-020, T-021 | [x] confirmado | mapper, componente e E2E com `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED` |
| FR-003 — baixar documento concluído | T-017, T-019, T-020, T-021 | [~] parcialmente confirmado | streaming, headers, Blob e MinIO real foram testados; não há evidência de smoke manual nem de interrupção real do stream |
| FR-004 — confirmar e excluir definitivamente | T-018, T-020, T-021 | [x] confirmado | confirmação/cancelamento no componente, lock pessimista, remoção de registro e objetos no E2E |
| FR-005 — tratar falhas parciais da exclusão | T-018, T-019, T-021 | [x] confirmado | testes de falha de storage preservam o registro e não reportam sucesso falso |
| FR-006 — atualizar a listagem no frontend | T-018, T-020, T-021 | [x] confirmado | remoção local após sucesso; falha mantém o item; E2E confirma exclusão no backend |
| FR-007 — erros de download/exclusão e concorrência | T-017, T-018, T-019, T-020 | [~] parcialmente confirmado | `404`, `409`, `503`, consumidor tardio e lock cobertos; contrato genérico de `500` não tem teste HTTP dedicado |
| FR-008 — lista vazia | T-016, T-019, T-020, T-021 | [x] confirmado | backend retorna coleção vazia e frontend exibe `Nenhum documento encontrado` |
| FR-009 — erro de listagem e retry | T-016, T-019, T-020 | [x] confirmado | mensagem exata, estado de erro e nova tentativa cobertos no mapper/componente |

## 4. Critérios de aceite

| Critério | Resultado | Evidência / ressalva |
|---|---|---|
| AC-001 — listar todos os documentos/status | [x] | testes da página e E2E |
| AC-002 — download com conteúdo e headers aprovados | [~] | testes de API, componente e MinIO real; sem execução manual no navegador |
| AC-003 — exclusão confirmada remove registro e objetos | [x] | serviço, integração e E2E |
| AC-004 — erro de listagem exibe mensagem aprovada e permite retry | [x] | `document-error.mapper.ts` e `document-pages.spec.ts` |
| AC-005 — falha de exclusão mantém o item visível | [x] | teste de componente e serviço |
| AC-006 — cancelar confirmação não chama o backend | [x] | teste de componente |
| AC-007 — lista vazia exibe mensagem aprovada | [x] | testes de serviço e componente |

## 5. Requisitos de prioridade alta do plano de verificação

O plano não usa uma matriz formal P0/P1. A revisão considera prioritários os itens
explicitamente classificados como risco alto e os fluxos críticos da Fase 4/T-023.

- [x] Listagem preserva os sete campos e os quatro status existentes.
- [x] Lista vazia e erro de listagem estão cobertos, incluindo a mensagem aprovada e retry.
- [x] Download `COMPLETED` retorna bytes, `Content-Type`, `Content-Length`,
      `Content-Disposition: attachment` e `Cache-Control: no-store`.
- [x] Download de `PENDING`, `PROCESSING` e `FAILED` é rejeitado com `409`.
- [x] Documento ausente retorna `404` e storage indisponível retorna `503` nos cenários testados.
- [x] Exclusão definitiva cobre registro, objeto final, staging, falha parcial, lock
      concorrente e mensagem tardia do consumidor.
- [x] Confirmação, cancelamento e atualização local da listagem estão cobertos.
- [x] Upload e consulta por identificador continuam verdes nas suítes de regressão.
- [ ] O contrato de falha inesperada `500 application/problem+json` não foi confirmado
      por um teste HTTP dedicado para listagem/download/exclusão.
- [ ] O fluxo completo no navegador real não foi executado; a evidência disponível é
      automatizada por testes Angular/HttpTestingController e backend/Testcontainers.

## 6. Divergências e itens que não puderam ser confirmados

1. **Plano versus comportamento de download no frontend — ressalva de contrato.**
   O plano diz que a ação de download deve ficar disponível somente quando o backend
   confirmar a disponibilidade. O componente atual renderiza o botão para todos os
   status e depende do backend para rejeitar estados não concluídos com `409`.
   A Spec exige o bloqueio no backend e os testes cobrem o `409`, mas o texto do plano
   sugere uma UX mais restritiva. Isso precisa ser alinhado antes do deploy ou aceito
   explicitamente pelo aprovador.

2. **Contrato de erro 500 — não confirmado integralmente.** O plano/documentação
   mencionam `500 application/problem+json`, porém não foi localizado teste HTTP
   dedicado que comprove esse formato para uma falha inesperada de listagem, download
   ou exclusão. Os testes frontend simulam `500`, mas isso não comprova o handler real
   do backend.

3. **E2E versus navegador — cobertura parcial.** O T-021 foi coberto por integração
   do backend com Testcontainers e por testes Angular/HTTP. Não foi executado um smoke
   manual ou browser E2E atravessando a aplicação frontend real até a API.

4. **Status documental da Spec.** As tarefas T-001 a T-024 estão marcadas como
   concluídas, conforme aprovação registrada, mas o checklist de DoD da própria Spec
   permanece desmarcado. Não alterei esse gate porque a aprovação final é do Marcos.

5. **Artefato de entrega.** A revisão foi feita sobre o worktree atual; não foi
   estabelecido um commit/ref limpo específico para deploy. Há arquivos não relacionados
   no worktree, portanto a promoção deve usar um artefato/ref revisado explicitamente.

## 7. Arquitetura, riscos e regressão

- [x] A implementação permanece nos limites de frontend/backend e domínio de documentos
      já definidos; não foi criada estrutura arquitetural nova.
- [x] Não houve migration nem alteração de schema nesta feature.
- [x] O risco de exclusão entre PostgreSQL, storage final, staging e RabbitMQ está
      registrado no ADR-006, com política de ordem, lock, falha parcial e consumidor tardio.
- [x] O frontend não acessa diretamente MinIO, RabbitMQ ou PostgreSQL e não expõe `objectKey`.
- [x] Não há indício de regressão nas features existentes de upload e consulta por
      identificador: os testes existentes permaneceram verdes e o E2E preserva esses fluxos.
- [~] A ausência de smoke manual/browser deixa não confirmado o comportamento visual e
      de foco/teclado em ambiente real, embora haja cobertura de componente para esses casos.

## 8. Gate final

- [x] FRs e ACs foram comparados com código e testes.
- [x] Riscos e ADR foram revisados.
- [x] Divergências e lacunas foram registradas acima.
- [x] Nenhum deploy foi executado nesta revisão.
- [ ] Deploy aprovado — decisão pendente do Marcos.

Conclusão: a maior parte da implementação converge com a Spec, o plano e os testes,
mas esta revisão não confirma integralmente o contrato genérico de `500`, não executa
smoke no navegador real e registra a diferença entre a UX de download implementada e a
redação do plano. Esses pontos devem ser considerados na decisão de aprovação do deploy.
