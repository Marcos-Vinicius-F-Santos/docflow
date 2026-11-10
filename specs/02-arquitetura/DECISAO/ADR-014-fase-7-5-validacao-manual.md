# ADR-014 — Fase 7.5 para validação manual antes das evidências finais

- **Status:** aceito
- **Data:** 2026-11-10
- **Escopo:** validação entre a Fase 7 e o pacote final da Fase 8

## Contexto

As fases automatizadas e operacionais foram concluídas, mas o fluxo publicado ainda
precisa ser exercitado pelo responsável do projeto como usuário real. A validação é
especialmente importante para autenticação Neon, upload, processamento assíncrono,
notificações e tratamento de `401`, que dependem de sessão e navegador reais.

## Decisão

Adicionar a Fase 7.5 como gate obrigatório antes da Fase 8. Nenhuma afirmação final
será consolidada enquanto o fluxo manual não for executado e os defeitos encontrados
não forem corrigidos ou registrados como limitações.

## Consequências

- o projeto ganha uma etapa explícita de teste de aceitação manual;
- problemas encontrados podem gerar commits corretivos dentro da fase;
- evidências técnicas devem remover tokens, cookies, conteúdo e dados pessoais;
- a Fase 8 permanece documental até o fluxo estar aprovado.
