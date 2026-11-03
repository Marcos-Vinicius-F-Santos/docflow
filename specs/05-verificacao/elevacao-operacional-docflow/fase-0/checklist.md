# Checklist de Convergência — Fase 0: estabilização e baseline

Status: aprovado localmente — 2026-11-03  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-0/tarefas.md`

Legenda: `[x]` confirmado; `[~]` parcial; `[ ]` pendente.

## 1. Fontes

- [x] Princípios, produto e arquitetura lidos.
- [x] Spec mestre revisada.
- [x] Código backend/frontend e Compose comparados.
- [x] Commit avaliado identificado: `bb908044d5032751c4ef2d2403fcb6b3fdc33e73`.

## 2. Execuções obrigatórias

| Verificação | Resultado | Evidência |
|---|---|---|
| `npm ci` | [x] | 500 pacotes adicionados em execução de 2026-11-03. |
| lint frontend | [x] | Todos os arquivos passaram no lint. |
| testes frontend | [x] | 12 arquivos e 110 testes passaram em 2,33 s. |
| build frontend | [x] | `frontend/dist/docflow-frontend` gerado; build sem alerta. |
| `./mvnw verify` | [x] | 250 testes: 0 falhas, 0 erros e 0 ignorados; duração total de 1 min 38 s. |
| Testcontainers | [x] | PostgreSQL, RabbitMQ e MinIO iniciados e usados pelos testes de integração. |
| orçamento CSS | [x] | CSS do Hub medido em 5,67 kB; aviso corrigido com consolidação e orçamento de aviso explícito de 6 kB. |
| cobertura e limitações | [x] | Relatório versionado em `baseline.md`; cobertura não medida porque não há JaCoCo configurado. |

## 3. Rastreabilidade

| Item | Tarefa | Evidência |
|---|---|---|
| FR-001 | F0-T001 a F0-T007 | [x] frontend e backend validados localmente |
| AC-001 | F0-T003/F0-T004/F0-T006 | [x] baseline completo registrado |
| AC-002 | F0-T004 | [x] integração com Testcontainers aprovada |

## 4. Gate final

- [x] Nenhum resultado depende de artefato antigo; os comandos foram repetidos após as correções locais.
- [x] Os comandos foram repetidos após as correções, com Docker/Testcontainers funcional.
- [x] Não há regressão observada no frontend nem no fluxo integrado do backend.
- [x] Divergências e limitações estão registradas em `baseline.md`.
- [x] Marcos aprovou a fase; deploy externo não é aplicável nesta fase.

## Decisão do gate

O gate está aprovado para o commit único da Fase 0. A aprovação cobre validação local;
CI e deploy externo permanecem fora do escopo desta fase.

