# Revisão de entrega — Recebimento de conteúdo binário

> Revisão histórica do estado anterior à consolidação do backend operacional. A fonte
> vigente é `specs/03-features/backend-operacional-docflow/spec.md`, com operação local
> em `docs/document-registration-operations.md` e deploy/rollback em
> `specs/06-deploy/registro-binario-e-armazenamento-assincrono-documentos/deploy.md`.

Data da revisão: 2026-09-16  
Status: revisão preparada; não aprovado para deploy

## Checklist de convergência

| Verificação | Situação | Evidência/observação |
|---|---|---|
| Spec da feature aprovada | Concluída | `specs/03-features/entrada-binaria-documentos/spec.md` |
| Plano e tarefas alinhados | Concluída | `specs/04-plano/entrada-binaria-documentos/` |
| Contrato multipart e `DocumentContent` | Concluída | Controller, DTO e testes MVC |
| Validação Tika e limite funcional | Concluída | Testes do validador e staging |
| Staging e fechamento do stream | Concluída | Testes do adaptador MinIO |
| Regressão do backend | Concluída | 49 testes passaram, incluindo PostgreSQL/Testcontainers |
| Publisher RabbitMQ operacional | Pendente | Existe porta/contrato, mas não adaptador/topologia executável |
| Limite HTTP de upload configurado | Pendente | `spring.servlet.multipart.*` precisa ser confirmado no ambiente |
| ADR-004 aceita | Pendente | O documento ainda está com status `Proposta` |
| Tratamento final de falha de staging | Pendente | Falhas de storage não foram convertidas em `400` |
| Aprovação final de Marcos | Pendente | Gate obrigatório antes de deploy |

## Divergências residuais

1. O teste T-021 cobre o contrato provider-neutral, não uma integração RabbitMQ real,
   porque a topologia e o adaptador ainda não existem.
2. O limite de `52428800` bytes é aplicado no fluxo de staging, mas a configuração
   preventiva do limite HTTP ainda não está presente na aplicação.
3. A resposta `400` está implementada para validações de conteúdo; uma falha de
   infraestrutura do staging permanece como falha de storage, sem ser mascarada como
   erro de entrada.
4. O Application Service gera o UUID antes de persistir para formar a chave de staging.
   Uma falha posterior de persistência pode deixar objeto órfão, conforme o risco sem
   transação distribuída registrado na arquitetura.

## Gate de entrega

Não executar deploy enquanto os itens pendentes acima não tiverem decisão e aprovação
explícitas. A aprovação das tarefas não equivale à aprovação do deploy; o aprovador final
deve revisar este checklist, os riscos e o procedimento de rollback.
