# Registro de Decisão — Entrada binária com staging assíncrono

Data: 2026-09-16  
Status: Aceita

## Contexto

O `POST /documents` atualmente recebe apenas JSON com metadados. A feature aprovada
passa a exigir uma parte binária `file`, mas o `InputStream` associado à requisição HTTP
não pode ser enviado a um consumidor RabbitMQ depois que a requisição terminar.

O backend já possui a fronteira provider-neutral `DocumentStorage` e um adaptador MinIO,
mas o fluxo assíncrono precisa de um objeto durável antes da publicação. O bucket de
staging também deve ser isolado do bucket final.

## Decisão

O fluxo de registro binário será assíncrono. Durante a própria requisição HTTP, a camada
de aplicação gravará o conteúdo por uma porta provider-neutral de staging, implementada
por um adaptador que reutiliza o cliente MinIO existente e usa o bucket separado
`docflow-staging`. Depois da gravação, a aplicação publicará na mensageria somente o
`contentReference` no formato lógico `staging/{documentId}`; o `InputStream` nunca será
transportado na mensagem.

A camada de aplicação não dependerá diretamente do SDK do MinIO. Não será criada uma
transação distribuída, outbox ou migration nesta decisão. A substituição do contrato JSON
anterior por `multipart/form-data` é intencional e pertence à Spec da Feature.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Enviar o `InputStream` diretamente para um consumidor assíncrono | O stream está vinculado à requisição HTTP e deixa de existir quando ela termina. |
| Publicar o binário dentro da mensagem RabbitMQ | Aumenta o acoplamento e o custo do broker; contradiz a separação entre conteúdo e mensagem. |
| Gravar o staging no bucket final | Mistura conteúdo intermediário e final e impede políticas de retenção/lifecycle distintas. |
| Fazer o controller acessar diretamente o MinIO | Cruza o limite arquitetural entre API e adaptadores externos. |
| Usar o fluxo síncrono como caminho desta feature | Não atende ao processamento assíncrono e ao contrato de mensageria definidos para o fluxo. |

## Consequências

O conteúdo sobrevive ao encerramento da requisição, o consumidor pode recuperar o objeto
por referência e o staging pode ter políticas operacionais próprias. A aplicação mantém
independência do SDK do MinIO.

Como custo, a requisição fica bloqueada durante a gravação no staging e a operação passa
a coordenar PostgreSQL, MinIO e RabbitMQ sem atomicidade distribuída. Falhas entre essas
etapas podem deixar objetos órfãos ou exigir compensação/reconciliação futura. A porta de
staging e o contrato do publisher precisam permanecer alinhados com a feature de
mensageria.
