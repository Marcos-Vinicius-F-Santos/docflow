# Tarefas — Fase 7.5: validação manual do fluxo e correções

Spec relacionada: `specs/03-features/elevacao-operacional-docflow/spec.md`
Dependência: Fases 0–7 commitadas; ambiente local e publicação disponíveis.

## Preparação

- [ ] F75-T001 Registrar URLs, commit testado, navegador, ambiente e data da sessão.
- [ ] F75-T002 Confirmar que o backend, frontend, Neon Auth, PostgreSQL, RabbitMQ e MinIO estão acessíveis.
- [ ] F75-T003 Preparar um PDF válido, um arquivo inválido e uma massa de teste identificável.

## Fluxo feliz

- [ ] F75-T004 Criar conta ou confirmar conta existente pelo DocFlow.
- [ ] F75-T005 Entrar, sair e entrar novamente, confirmando a sessão exibida no frontend.
- [ ] F75-T006 Carregar a lista de documentos autenticada.
- [ ] F75-T007 Enviar PDF válido e acompanhar `PENDING` → `PROCESSING` → `COMPLETED`.
- [ ] F75-T008 Confirmar notificação em tempo real e atualização após recarregar a página.
- [ ] F75-T009 Abrir preview, baixar e conferir nome, tipo e conteúdo do arquivo.
- [ ] F75-T010 Excluir o documento e confirmar que ele não reaparece na lista.

## Falhas e sessão

- [ ] F75-T011 Enviar arquivo inválido e confirmar erro compreensível no frontend.
- [ ] F75-T012 Testar retry de documento falho, sem duplicação de registro ou objeto.
- [ ] F75-T013 Expirar/remover a sessão e confirmar resposta tratada para `401`, sem loop infinito.
- [ ] F75-T014 Confirmar que usuário anônimo não acessa documentos nem logs protegidos.
- [ ] F75-T015 Conferir WebSocket, logs operacionais e alertas sem expor token, cookie ou conteúdo.

## Correção controlada

- [ ] F75-T016 Registrar cada defeito com passos, request, status, evidência e impacto.
- [ ] F75-T017 Reproduzir cada defeito em ambiente controlado antes de alterar o código.
- [ ] F75-T018 Corrigir somente o escopo reproduzido e adicionar teste de regressão.
- [ ] F75-T019 Reexecutar o fluxo completo após cada correção.
- [ ] F75-T020 Registrar limitações que permanecerem e separar defeito de limitação externa.
- [ ] F75-T021 Criar o commit completo da fase após o fluxo estar aprovado.

## Gate da Fase 7.5

- [ ] Fluxo autenticado completo aprovado.
- [ ] Fluxo de falha e sessão expirada aprovado.
- [ ] Problemas encontrados têm correção ou limitação registrada.
- [ ] Testes e regressão executados após as correções.
- [ ] Marcos aprovou o commit da fase.
