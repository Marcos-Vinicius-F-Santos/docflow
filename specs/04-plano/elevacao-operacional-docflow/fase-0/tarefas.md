# Tarefas — Fase 0: estabilização e baseline

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Requisitos: FR-001  
Gate: nenhuma tarefa de CI/CD ou cloud começa antes desta fase estar aprovada.

## Regra das tarefas

Cada tarefa deve ser pequena o suficiente para revisão isolada, indicar sua verificação
e deixar o checkout funcional. Marcar uma tarefa como concluída exige evidência real.

## Implementação

- [x] F0-T001 Mapear comandos atuais de backend, frontend, Docker Compose e Testcontainers, registrando versões, pré-requisitos e limitações.
  - Verificação: outra pessoa consegue reproduzir os comandos a partir de checkout limpo.
- [x] F0-T002 Corrigir a resolução de `@angular/core` no Vitest sem mascarar falhas de import ou desabilitar testes.
  - Verificação: o teste que falhava passa e a configuração continua resolvendo dependências reais.
- [x] F0-T003 Executar `npm ci`, lint, testes e build do frontend e corrigir falhas de configuração ou regressões encontradas.
  - Verificação: os quatro comandos terminam com código zero em execução limpa.
- [x] F0-T004 Executar Maven com Docker/Testcontainers ativo e corrigir falhas de isolamento, contexto ou dependências.
  - Verificação: `./mvnw verify` termina sem erros de integração.
- [x] F0-T005 Corrigir o alerta de orçamento CSS sem aumentar limites silenciosamente; qualquer aumento deve ser justificado.
  - Verificação: o build não apresenta o alerta ou a exceção está documentada.
- [x] F0-T006 Registrar número de testes, cobertura, duração e limitações em um relatório versionado.
  - Verificação: números são provenientes da execução atual, com data, commit e comandos.
- [x] F0-T007 Atualizar README para separar validação local, validação CI e publicação externa.
  - Verificação: nenhuma seção apresenta resultado local como CI ou publicação.
- [x] F0-T008 Criar o commit completo da Fase 0 somente depois do checklist e da execução aprovados.
  - Verificação: `git show --stat` contém código/configuração/testes/documentação da fase e o commit é reproduzível.

## Gate da Fase 0

- [x] FR-001 atendido.
- [x] AC-001 e AC-002 atendidos.
- [x] Regressão do fluxo de documentos executada com Testcontainers.
- [x] Checklist de validação aprovado.
- [x] Execução registrada e rollback conhecido.
- [x] Marcos aprovou a execução e o commit da fase no escopo solicitado.

## Status da execução

Todas as tarefas F0-T001 a F0-T008 foram concluídas localmente. O `verify` completo
passou com Docker/Testcontainers funcional e o commit único está sendo criado após a
atualização dos registros. Evidências: [`baseline.md`](../../../05-verificacao/elevacao-operacional-docflow/fase-0/baseline.md),
[`checklist.md`](../../../05-verificacao/elevacao-operacional-docflow/fase-0/checklist.md)
e [`execucao.md`](../../../06-deploy/elevacao-operacional-docflow/fase-0/execucao.md).

