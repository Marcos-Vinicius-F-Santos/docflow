# Registro de Decisão — Separação entre metadados e conteúdo binário

Data: 2026-09-14  
Status: Aceita

## Contexto

Documentos possuem metadados e conteúdo binário com características diferentes. O
projeto usa PostgreSQL para os metadados/estado e MinIO para o conteúdo binário. Esses
sistemas não compartilham uma transação distribuída, portanto uma operação pode ter
resultado parcial ou inconclusivo.

## Decisão

Metadados, estado e referência estável do documento serão persistidos em PostgreSQL; o
conteúdo binário será persistido em MinIO por meio da porta provider-neutral
`DocumentStorage`. O `objectKey` será baseado no UUID do documento, e o adaptador MinIO
traduzirá falhas do SDK para `UNAVAILABLE`, `REJECTED` e `RESULT_UNKNOWN`.

O sistema não reportará conclusão sem confirmação da gravação e persistência da
referência estável. Para `RESULT_UNKNOWN`, a aplicação mantém o documento em
`PROCESSING`, usa retry/reconciliação por `exists(objectKey)` e só conclui após
confirmação ou após a transição final para `FAILED`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Persistir metadados e binário no PostgreSQL | Mistura responsabilidades de persistência e não é a separação definida para o projeto. |
| Fazer o serviço depender diretamente do SDK do MinIO | Acopla a regra de negócio a um fornecedor específico e dificulta sua substituição/teste. |
| Usar uma transação distribuída entre PostgreSQL e MinIO | Os sistemas não oferecem uma transação distribuída compartilhada; assumir atomicidade seria incorreto. |
| Gerar o `objectKey` sem relação estável com o documento | Dificulta correlação, consulta e reconciliação do objeto com seu registro. |

## Consequências

Essa decisão mantém o domínio independente do SDK e torna explícita a diferença entre
estado do registro e confirmação do armazenamento. O UUID fornece uma referência
estável e correlacionável.

Como custo, o sistema precisa lidar com consistência entre dois sistemas sem uma única
transação. Operações de recuperação e reconciliação são necessárias e permanecem
explicitamente fora de uma transação distribuída.
