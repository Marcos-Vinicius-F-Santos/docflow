# Regras para Agentes de IA neste Repositório

Este projeto segue Spec-Driven Development. Estas regras valem pra qualquer IA (Claude Code,
Claude, etc.) que for gerar ou alterar código aqui.

## Antes de tocar em código

1. Leia `specs/00-principios/principios.md`.
2. Leia a Spec do Produto em `specs/01-produto/`.
3. Leia a Spec da Feature específica em `specs/03-features/<feature>/spec.md` e o
   plano/tarefas correspondente em `specs/04-plano/<feature>/`.
4. Se a spec da feature não existir ainda, **pare e peça pra eu criar uma antes de
   escrever código** — não invente requisito, não assuma comportamento.

## Comportamento obrigatório

- **Não invente funcionalidade fora da spec.** Se algo está ambíguo e isso afeta o
  comportamento, registre como "Suposição" ou "Pergunta aberta" na spec da feature em vez
  de decidir por conta própria.
- **Respeite a arquitetura de pastas já definida** em `specs/02-arquitetura/` (organização
  por feature/domínio, não por tipo de arquivo).
- **Não altere comportamento de features existentes** sem isso estar explícito na tarefa
  atual — se perceber que precisa, avise antes de fazer.
- **Siga a ordem incremental**: implemente uma tarefa por vez, na ordem do arquivo de
  tarefas, não pule fases.
- **Trate segredos como não-commitáveis** — nunca hardcode chave/senha/token; use variável
  de ambiente.
- Para mudanças de schema/banco de dados, sempre crie o equivalente de uma migration —
  nunca altere dado existente direto sem caminho de volta.

## Convergência antes de dizer "pronto"

Antes de reportar uma tarefa como concluída, compare:

```text
SPEC DA FEATURE ↔ PLANO/TAREFAS ↔ CÓDIGO ↔ TESTE
```

Se algo divergiu (ex: implementou diferente do planejado porque fazia mais sentido),
diga isso explicitamente em vez de deixar a spec desatualizada em silêncio.

## Critério de conclusão de uma tarefa

Uma tarefa só está pronta quando:
- o critério de aceite da feature (Given/When/Then) é satisfeito;
- as features existentes continuam funcionando (sem regressão óbvia);
- o teste do nível de prioridade indicado no plano de verificação foi feito;
- eu (Marcos) revisei e aprovei antes do deploy.

## Lembrete final

Eu sou o aprovador final em todo portão do ciclo (spec do produto, spec de cada feature,
deploy). Velocidade nunca substitui essa aprovação.
