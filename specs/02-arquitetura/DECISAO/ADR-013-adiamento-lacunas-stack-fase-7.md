# ADR-013 — Adiamento das lacunas específicas de stack da Fase 7

- **Status:** aceito
- **Data:** 2026-11-06
- **Escopo:** Fase 7 da elevação operacional do DocFlow

## Contexto

A Fase 7 permite avaliar MongoDB, Python e Kafka quando houver requisito concreto de
vaga, caso de uso real e fronteira arquitetural aprovada. O DocFlow atual já possui
fluxo funcional com PostgreSQL, RabbitMQ, MinIO, Spring Boot, Angular e Kubernetes
local.

## Decisão

Adiar a Fase 7 sem adicionar MongoDB, Python ou Kafka ao DocFlow. A fase não receberá
dependências, serviços, código ou infraestrutura apenas para preencher a stack.

As tecnologias listadas como fora do escopo continuam reservadas para projetos
separados, salvo nova aprovação e nova spec. Uma futura necessidade deverá apresentar:

1. requisito concreto e rastreável;
2. caso de uso que não seja atendido pela stack atual;
3. spec, ADR, custo, segurança, operação e rollback aprovados;
4. testes de integração e regressão que demonstrem valor.

## Consequências

- custo e complexidade permanecem inalterados;
- a superfície operacional do DocFlow não aumenta;
- a ausência dessas tecnologias fica explícita e defensável no portfólio;
- qualquer introdução futura será uma decisão isolada, não uma expansão implícita desta fase.
