# Registro de Decisão — RabbitMQ para fluxos assíncronos

Data: 2026-09-14  
Status: Aceita

## Contexto

O projeto trabalha com fluxos assíncronos no registro binário operacional. É necessário
separar a entrada de comandos HTTP do processamento posterior e manter a possibilidade
de evolução dos consumidores sem acoplar o controller a cada integração. O baseline
histórico desta decisão trabalhava apenas com metadados; o fluxo operacional de
armazenamento assíncrono foi posteriormente ativado pela Spec da Feature consolidada.

## Decisão

RabbitMQ será o mecanismo de mensageria dos fluxos assíncronos do DocFlow. Publishers,
consumidores e contratos de mensagens serão definidos por feature, antes da
implementação correspondente.

O escopo temporal original desta decisão — não utilizar RabbitMQ na fase inicial — foi
revisado para o fluxo de entrada binária pela ADR-004. A ativação operacional desse
fluxo está descrita na Spec da Feature de backend operacional e nos documentos de
operação/deploy vigentes.

Para o fluxo de armazenamento de documentos, a Spec consolidada define exchange, filas,
publisher confirm, retries, DLX/DLQ e requeue. Outras features continuam obrigadas a
definir seus próprios contratos antes de criar mensageria.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Processar todo o fluxo de forma síncrona na requisição HTTP | Não atende à decisão de trabalhar com fluxos assíncronos e mantém o controller acoplado ao tempo do processamento. |
| Eventos apenas em memória dentro do processo | Não oferece a mesma durabilidade e separação entre produtor e consumidor esperadas para a mensageria do projeto. |
| Adotar outro broker | RabbitMQ foi a escolha confirmada para o projeto. |

## Consequências

Quando adotado, RabbitMQ permitirá que produtores e consumidores fiquem desacoplados e
que o processamento evolua independentemente da API. A decisão também criará
dependência operacional de um broker e exigirá contratos de mensagem, observabilidade,
tratamento de retries e definição de idempotência por feature.

Os detalhes de retry, DLX/DLQ, confirmação, requeue, concorrência e idempotência do
fluxo de armazenamento estão definidos na Spec da Feature de backend operacional e
na ADR-005. Nenhum detalhe deve ser generalizado para outras features sem contrato
próprio.
