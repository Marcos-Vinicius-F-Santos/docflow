# Registro de Decisão — Exclusão definitiva de documentos

Data: 2026-09-20  
Status: Aceita no plano aprovado da feature  
Feature: `listagem-download-exclusao-documentos`

## Contexto

A feature aprovada permite excluir definitivamente documentos em qualquer estado
listado: `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.

O registro do DocFlow usa PostgreSQL para metadados e estado, MinIO para staging e
storage final e RabbitMQ para o processamento assíncrono. Esses sistemas não participam
de uma transação distribuída. O documento não persiste uma referência de staging, mas a
referência publicada é determinística (`staging/{documentId}`), assim como a referência
do storage final (`documents/{documentId}`).

Excluir primeiro o registro do PostgreSQL pode deixar objetos órfãos sem uma referência
de negócio para uma nova tentativa. Excluir primeiro os objetos permite repetir a
operação enquanto o registro ainda existe, mas exige tratar uma falha posterior na
remoção do registro.

Documentos `PENDING` e `PROCESSING` também podem possuir mensagem RabbitMQ pendente ou
em processamento. A exclusão não deve permitir que o consumidor recrie o documento ou
conclua uma operação depois que o registro foi removido.

## Decisão

1. A exclusão será física e definitiva; não será criado estado de exclusão lógica,
   coluna de tombstone ou histórico de lixeira.
2. O caso de uso deve obter lock transacional da linha do documento antes de iniciar a
   remoção, serializando a operação com o claim do consumidor e a reconciliação já
   existentes.
3. O caso de uso deve remover primeiro o objeto final pela porta `DocumentStorage`,
   usando `document.getObjectKey()` quando existir e, caso contrário, a chave
   determinística `documents/{documentId}`.
4. O caso de uso deve remover também o staging determinístico `staging/{documentId}` por
   uma porta provider-neutral de staging. A remoção de objeto inexistente deve ser
   tratada como sucesso idempotente pelo adaptador.
5. Somente depois de concluir as remoções de storage o caso de uso deve remover o
   registro do PostgreSQL na mesma operação transacional da aplicação.
6. Se uma remoção de storage falhar, o registro deve permanecer e a API não deve
   responder sucesso. A repetição manual deve poder executar novamente as remoções.
7. Se a remoção dos objetos for confirmada e a remoção do registro falhar, o registro
   permanece visível para uma nova tentativa; o storage não deve ser recriado.
8. Não será criado cancelamento RabbitMQ nesta feature. Se uma mensagem já publicada
   chegar depois que o registro foi removido, o consumidor deve tratá-la como mensagem
   inválida de documento ausente e encaminhá-la diretamente para a DLQ, sem criar
   registro, armazenar conteúdo ou fazer retry de negócio.
9. O endpoint não aceitará `objectKey`, `contentReference` ou referências de storage do
   cliente; todas as chaves serão derivadas ou lidas internamente.

### Extensão aprovada para exclusão em lote

10. A exclusão em lote usa uma única requisição `DELETE /documents` com corpo
    `{ "documentIds": [...] }`. O endpoint individual `DELETE /documents/{documentId}`
    permanece inalterado.
11. A resposta de uma requisição válida é `200 OK` com `results` por documento. Cada
    item pode terminar como `DELETED`, `FAILED` ou `NOT_FOUND`; uma falha de um item não
    deve apagar o resultado dos demais.
12. Cada documento é processado em uma unidade transacional/operacional independente,
    mantendo o lock pessimista e a ordem de remoção já definida. O resultado parcial
    não transforma falha de storage ou de persistência em sucesso.
13. Documentos `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED` são elegíveis para a
    exclusão em lote. Para `PENDING` e `PROCESSING`, a corrida com consumidor, retry ou
    reconciliação segue as regras desta decisão: não há cancelamento RabbitMQ; uma
    mensagem posterior que encontre o documento ausente vai diretamente para a DLQ.
14. Uma requisição inválida inteira — por exemplo, corpo ausente ou coleção de IDs
    inválida — responde `400 application/problem+json`. Um documento inexistente é
    resultado `NOT_FOUND` do item e não invalida os demais IDs.

Essa decisão não cria migration. Se a implementação descobrir que o lock existente,
as referências determinísticas ou os adaptadores atuais não são suficientes para cumprir
essas regras, a implementação deve parar e abrir nova decisão antes de alterar schema,
mensagem ou estados de domínio.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Remover o registro antes dos objetos | Pode deixar objetos órfãos sem referência de negócio e permitir corrida com mensagens pendentes. |
| Criar estado `DELETING` ou tombstone | Adiciona estado de domínio, coluna e fluxo de recuperação não aprovados na Spec. |
| Criar cancelamento RabbitMQ | Exige novo contrato/topologia e não garante retirar mensagens já entregues ao consumidor. |
| Usar transação distribuída PostgreSQL/MinIO | Não é suportada pelos sistemas e contradiz os ADRs existentes. |
| Excluir somente o registro do PostgreSQL | Não satisfaz a exclusão definitiva do conteúdo binário. |

## Consequências

A operação é repetível enquanto o registro existir, não expõe infraestrutura ao cliente e
mantém a regra de negócio no serviço de aplicação. O lock reduz a corrida com o
consumidor e a reconciliação.

Como custo, a transação pode manter lock de linha durante chamadas ao storage e uma
falha depois da remoção dos objetos pode deixar um registro sem conteúdo até uma nova
tentativa. A operação não é reversível por rollback de código depois que os objetos foram
removidos.
