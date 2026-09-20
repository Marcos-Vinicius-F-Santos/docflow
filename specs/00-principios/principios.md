# Princípios de Engenharia — Marcos

Status: Ativo
Última revisão: preencher

Esses princípios não mudam de projeto pra projeto. A profundidade dos documentos muda
(ver README.md, seção "Escala por tipo de projeto"), os princípios não.

## 1. Simplicidade e incremento

- Construo uma funcionalidade fina e completa por vez (ficha → estilo → automação →
  multiusuário, no estilo do Assimilação-RPG), nunca todas as camadas de uma vez.
- A arquitetura mais simples que atende o que já está confirmado hoje é melhor que a
  arquitetura "preparada pro futuro" que ninguém confirmou que vai precisar.

## 2. Spec antes de código

- Toda feature nova (fora os casos triviais listados em USAGE.md) tem uma spec curta
  antes do primeiro código, mesmo em projeto pessoal.
- A spec existe pra guiar decisão, implementação e teste — não pra "constar". Se não vai
  ser usada pra nenhuma dessas três coisas, não escrevo.

## 3. Arquitetura por domínio/feature

- Organizo o código por domínio/funcionalidade de negócio, não por tipo de arquivo —
  isso vale em qualquer stack: pastas `features/` em React, pacotes por domínio em Java,
  módulos por processo de negócio em Mendix.
- Só crio uma subdivisão nova quando ela realmente tem conteúdo suficiente pra
  justificar (regra prática: 2-3+ arquivos relacionados) — não subdivido antecipadamente.

## 4. Eu sou o aprovador

- Uso IA (Claude Code) pra implementar a maior parte dos meus projetos pessoais a partir da
  minha spec e arquitetura — exceto o Aegis-Core, onde escrevo o código eu mesmo porque é
  meu laboratório de P&D com IA.
- Em todos os casos, eu defino a spec, a arquitetura, as regras, e valido o resultado.
  A IA nunca aprova o próprio trabalho.

## 5. Teste por prioridade, não teste por igual

- Testo com mais rigor o caminho principal de cada feature e os erros mais óbvios.
  Edge case raro entra numa lista, não bloqueia entrega.
- Toda entrega passa por um teste de regressão mínimo: o que já funcionava continua
  funcionando?

## 6. Deploy é revisado, não automático por padrão

- Reviso o diff/comportamento final antes de aprovar o deploy, mesmo em projeto pessoal.
- Todo projeto tem, no mínimo, uma forma conhecida de voltar atrás rápido.

## 7. Independente de linguagem/plataforma

- Este jeito de trabalhar (spec → arquitetura → feature → teste → deploy) é o mesmo
  independente da stack — já usei esse raciocínio de decisão em OutSystems, Mendix/Java,
  SAP/ABAP e React/JS, e é assim que continuo operando em qualquer linguagem nova.
