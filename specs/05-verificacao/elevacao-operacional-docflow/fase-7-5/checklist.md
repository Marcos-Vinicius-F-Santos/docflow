# Checklist de Convergência — Fase 7.5: validação manual do fluxo e correções

Status: pronta para teste manual; nenhum resultado foi antecipado.
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-7-5/tarefas.md`

## Identificação da sessão

| Campo | Valor |
|---|---|
| frontend | a preencher durante o teste |
| backend | a preencher durante o teste |
| commit | a preencher durante o teste |
| navegador/dispositivo | a preencher durante o teste |
| data/hora | a preencher durante o teste |

## Fluxo principal

- [ ] Cadastro/login/logout/login novamente.
- [ ] Lista autenticada de documentos.
- [ ] Upload de PDF válido.
- [ ] Processamento assíncrono concluído.
- [ ] Notificação e atualização após reload.
- [ ] Preview e download conferidos.
- [ ] Exclusão confirmada.

## Falhas e segurança funcional

- [ ] Arquivo inválido mostra erro acionável.
- [ ] Retry funciona sem duplicação.
- [ ] Sessão expirada trata `401` e orienta o usuário.
- [ ] Usuário anônimo não acessa dados protegidos.
- [ ] WebSocket, logs e alertas não expõem segredos.

## Registro de defeitos

| ID | Sintoma | Como reproduzir | Evidência | Correção/limitação | Revalidado |
|---|---|---|---|---|---|
| — | Nenhum teste registrado ainda | — | — | — | — |

## Gate

- [ ] Todas as etapas críticas foram executadas.
- [ ] Correções possuem teste de regressão.
- [ ] O fluxo foi revalidado após as correções.
- [ ] Não há resultado inventado ou limitação externa omitida.
- [ ] Aprovação de Marcos obtida.
