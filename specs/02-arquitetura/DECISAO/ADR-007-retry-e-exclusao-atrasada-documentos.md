# Registro de Decisão — Retry via staging e exclusão atrasada de documentos

Data: 2026-10-27  
Status: Aceita  
Feature: hub

## Contexto

A feature Hub precisa permitir que o usuário tente novamente o processamento de um
documento FAILED sem enviar o arquivo novamente. O conteúdo já recebido permanece no
staging e deve ser reutilizado enquanto ainda houver falhas disponíveis.

A regra aprovada considera a falha do processamento inicial como a primeira falha. O
documento pode chegar a três falhas; nesse momento não deve haver uma quarta tentativa.
O conteúdo, o staging e os metadados devem ser removidos após três horas, mas o usuário
também pode solicitar a exclusão imediata.

PostgreSQL, RabbitMQ e MinIO não participam de uma transação distribuída. Portanto, o
contador de falhas, a marcação de exclusão, o prazo e a mensagem de exclusão precisam
ser persistidos e processados de forma idempotente. A decisão também precisa evitar
corridas entre retry, consumidor de armazenamento, exclusão manual e exclusão atrasada.

## Decisão

### Retry manual

1. O retry será exposto por POST /documents/{documentId}/retry.
2. O caso de uso adquirirá lock pessimista do documento antes de validar e publicar.
3. O retry será aceito somente quando:
   - status for FAILED;
   - failure_count for menor que 3;
   - is_excluded for false;
   - o staging determinístico staging/{documentId} ainda existir.
4. O retry reutilizará o staging existente, publicará uma nova solicitação de
   processamento e colocará o documento em PROCESSING.
5. A API responderá:
   - 202 Accepted quando a publicação for confirmada;
   - 404 quando o documento não existir;
   - 409 quando o documento não for elegível para retry;
   - 503 quando staging/storage estiver indisponível.
6. O contador não será incrementado ao clicar no retry. Cada falha efetiva de
   processamento incrementará failure_count.
7. Quando o processamento terminar com sucesso, o documento ficará COMPLETED,
   failure_count voltará para 0, is_excluded ficará false e
   exclusion_scheduled_at ficará null.
8. Uma solicitação repetida enquanto o documento estiver PROCESSING não poderá criar
   uma segunda mensagem efetiva para o mesmo documento.

### Persistência

A tabela documents receberá uma migration Flyway expand-only com:

- failure_count BIGINT NOT NULL DEFAULT 0, exposto no domínio como falhas;
- is_excluded BOOLEAN NOT NULL DEFAULT FALSE, exposto no domínio como isExcluded;
- exclusion_scheduled_at TIMESTAMPTZ NULL, exposto no domínio como
  exclusionScheduledAt.

A migration não editará V1–V5 e deverá manter documentos existentes legíveis. Serão
aplicadas as seguintes regras:

- failure_count >= 0;
- is_excluded = false OR failure_count >= 3;
- exclusion_scheduled_at IS NULL OR is_excluded = true.

Será criado um índice parcial para localizar documentos is_excluded elegíveis por
exclusion_scheduled_at.

Quando failure_count chegar a 3:

- o documento permanecerá FAILED;
- is_excluded será definido como true;
- exclusion_scheduled_at será definido para agora mais três horas;
- o documento será publicado na fila de exclusão;
- o Hub não oferecerá uma quarta tentativa.

### Topologia da exclusão

A exclusão atrasada usará uma topologia RabbitMQ própria e durável:

- exchange: docflow.document-deletion;
- fila de espera: docflow.document-deletion.delay.3h;
- fila consumida: docflow.document-deletion.requested;
- routing key inicial: document.deletion.scheduled;
- routing key após o TTL: document.deletion.requested;
- dead-letter exchange: docflow.document-deletion.dlx;
- dead-letter queue: docflow.document-deletion.dlq.

A fila de espera usará TTL de 3 horas e encaminhará a mensagem expirada para a fila
consumida. A mensagem será persistente e conterá somente schemaVersion e documentId.
O consumidor consultará o documento e derivará internamente as referências de storage.

Se a publicação da mensagem falhar, o documento permanecerá marcado para exclusão e
exclusion_scheduled_at será usado para recuperação e re-enfileiramento operacional.
A publicação exigirá publisher confirm e não poderá ser reconhecida como concluída antes
da confirmação correspondente.

### Contrato da mensagem e da topologia

A mensagem de exclusão terá schema versionado e persistente, sem referências de storage
fornecidas pelo cliente:

```json
{
  "schemaVersion": 1,
  "documentId": "00000000-0000-0000-0000-000000000000"
}
```

Os nomes e bindings aprovados são:

| Elemento | Nome/valor |
|---|---|
| Exchange | `docflow.document-deletion` |
| Fila de espera | `docflow.document-deletion.delay.3h` |
| Fila consumida | `docflow.document-deletion.requested` |
| Routing key inicial | `document.deletion.scheduled` |
| Routing key após TTL | `document.deletion.requested` |
| Dead-letter exchange | `docflow.document-deletion.dlx` |
| Dead-letter queue | `docflow.document-deletion.dlq` |
| Durabilidade | exchange, filas e mensagem persistentes |
| TTL da espera | `10800000` ms (3 horas) |

A fila de espera será ligada ao exchange principal com a routing key inicial e usará
`x-message-ttl` de três horas junto de `x-dead-letter-exchange` e
`x-dead-letter-routing-key` para encaminhar a mensagem à fila consumida após o prazo.
A fila consumida será ligada ao exchange principal pela routing key posterior. Mensagens
inválidas ou falhas de remoção que não possam ser confirmadas serão encaminhadas pela
rota de dead letter; o DLQ não pode ser tratado como sucesso de exclusão.

O publisher deverá aguardar publisher confirm antes de considerar o agendamento
publicado. O consumidor deverá confirmar (`ACK`) somente depois de: documento ausente
ser reconhecido como caso idempotente, ou remoção de storage e metadados terminar com
sucesso. Falhas de validação ou de storage/persistência devem usar `NACK` sem requeue,
permitindo a rota DLX/DLQ e recuperação operacional sem apagar o registro por atalho.
Não será criado um segundo retry automático de três horas para uma mensagem que já
chegou à fila consumida.

### Exclusão

O consumidor da fila seguirá a política de exclusão definitiva do ADR-006:

1. obter lock do documento quando ele ainda existir;
2. remover o objeto final pela porta DocumentStorage;
3. remover o staging determinístico pela porta de staging;
4. remover o registro de metadados do PostgreSQL;
5. confirmar a mensagem somente após o tratamento correspondente.

A remoção será idempotente. Se o usuário excluir o documento antes do vencimento da
fila, a mensagem atrasada posterior encontrará o documento ausente e será confirmada
sem recriar documento, staging ou objeto final.

A exclusão automática após três horas removerá o conteúdo binário, o staging e os
metadados persistidos. Não será criado um estado de negócio DELETING, tombstone ou
histórico de lixeira; is_excluded será um marcador operacional até a remoção definitiva.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Reenviar o arquivo original pelo frontend | Duplica upload, pode criar novo documento e não aproveita o staging já persistido. |
| Processar o retry de forma síncrona no endpoint HTTP | Acopla a resposta ao tempo do storage e contraria o fluxo assíncrono existente. |
| Criar um novo documento a cada retry | Perde a identidade do documento original, gera duplicidade e dificulta a contagem de falhas. |
| Usar somente um scheduler PostgreSQL para excluir após três horas | Não aproveita a mensageria aprovada para operações assíncronas e exige outro fluxo de execução; o timestamp continuará sendo usado para recuperação. |
| Usar uma fila única sem fila de espera | Não permite aplicar o atraso de três horas sem bloquear o consumidor da exclusão. |
| Apagar apenas o arquivo e preservar metadados | Contraria a decisão de exclusão completa após o prazo. |
| Criar exclusão lógica ou estado DELETING | Cria um novo estado de negócio e diverge do ADR-006, que define exclusão física definitiva. |
| Colocar objectKey, bucket ou referências de storage na mensagem | Expõe detalhes desnecessários e pode transportar referências obsoletas; o consumidor deriva as chaves internamente. |

## Consequências

A decisão mantém o documento como unidade de controle, reaproveita o staging, evita
reenvio de conteúdo, preserva a operação assíncrona e torna o prazo de exclusão
observável por exclusion_scheduled_at. O lock e a idempotência protegem as corridas
entre retry, consumidor e exclusão manual.

O custo é a criação de uma migration, um contrato HTTP novo, uma topologia RabbitMQ
adicional e um consumidor responsável por remoção irreversível. A fila TTL não substitui
a persistência do prazo; documentos marcados para exclusão precisam de recuperação
operacional caso a publicação inicial ou o consumo falhe.

A remoção dos objetos não é revertida por rollback de código. A exclusão automática e a
manual devem ser cobertas por testes de falha, integração e smoke test controlado antes
de qualquer deploy.

