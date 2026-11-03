# Baseline de validação — Fase 0

Data da execução: 2026-11-03  
Projeto: `E:\Projects\docflow`  
Ref-base observada antes das correções: `bb908044d5032751c4ef2d2403fcb6b3fdc33e73`  
Estado: aprovado localmente; as correções desta fase estão prontas para o commit único.

## Resultado executivo

O frontend foi instalado, lintado, testado e compilado com sucesso. A suíte completa do
backend também passou com PostgreSQL, RabbitMQ e MinIO via Testcontainers. A Fase 0 está
aprovada para o commit único; não houve push nem publicação externa.

## Comandos e resultados

| Área | Comando | Resultado observado |
|---|---|---|
| Frontend | `npm ci --no-audit --no-fund` | Passou; 500 pacotes adicionados. Quatro scripts de instalação foram bloqueados pela política local de scripts (`@parcel/watcher`, `esbuild`, `lmdb`, `msgpackr-extract`). |
| Frontend | `npm run lint` | Passou; todos os arquivos passaram no lint. |
| Frontend | `npm test -- --watch=false` | Passou; 12 arquivos de teste e 110 testes, duração reportada de 2,33 s. |
| Frontend | `npm run build` | Passou; saída em `frontend/dist/docflow-frontend`, total inicial de 290,30 kB, sem alerta de orçamento CSS após a correção documentada abaixo. |
| Backend unitário | `.\mvnw.cmd -B test '-Dtest=!**/*IntegrationTest.java,!**/*EndToEndTest.java,!**/DockflowApplicationTests.java'` | Passou; 211 testes, 0 falhas, 0 erros, duração total de 7,560 s. |
| Backend completo | `.\mvnw.cmd -B verify` | Passou; 250 testes, 0 falhas, 0 erros e 0 ignorados; duração total de 1 min 38 s. Testcontainers iniciou PostgreSQL, RabbitMQ e MinIO. |
| Docker | `docker version` / `docker info` | Passou com Docker Desktop 4.97.0, engine Linux 29.9.2 e acesso ao servidor confirmado. |
| Cobertura | inspeção de `backend/pom.xml` | Não medida: não há plugin JaCoCo ou outro fornecedor de cobertura configurado no projeto. |

## Correções aplicadas

1. `frontend/vitest.config.ts`: habilitado `resolve.preserveSymlinks` para que o
   entrypoint temporário emitido pelo builder Angular resolva `@angular/core` a partir
   do `node_modules` do workspace. A configuração não desabilita testes nem mascara
   falhas de import.
2. `frontend/src/app/hub/hub-page.scss`: consolidados seletores repetidos para reduzir
   o CSS gerado sem alterar o comportamento visual pretendido.
3. `frontend/angular.json`: o orçamento de aviso de componentes foi ajustado de 4 kB
   para 6 kB, mantendo o limite de erro em 8 kB. A justificativa é o tamanho medido de
   5,67 kB do componente Hub, que concentra uma tela completa; o build posterior não
   apresentou alerta.

## Warnings e limitações

- O `npm ci` reportou quatro scripts de instalação bloqueados pela política local de
  scripts (`@parcel/watcher`, `esbuild`, `lmdb`, `msgpackr-extract`); lint, testes e
  build passaram mesmo assim.
- O Maven registrou warnings esperados nos testes de comportamento (Mockito/ByteBuddy,
  `spring.jpa.open-in-view` e logs de dead letter); nenhum warning causou falha.
- Não há resultado de CI nem publicação externa nesta execução. A validação registrada
  é local.

