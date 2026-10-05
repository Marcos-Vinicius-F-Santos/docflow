# Operação — Registro de documentos com conteúdo binário

## Escopo

Este documento descreve a preparação e a operação do recebimento binário do domínio
`document`. O fluxo recebe `multipart/form-data`, grava o conteúdo no staging durante a
requisição HTTP e publica uma referência durável para o processamento assíncrono.

## Configuração

A aplicação lê as variáveis de `infra/.env`. O arquivo `infra/.env.example` contém
valores locais completos para que o ambiente não dependa de credenciais vazias. As
credenciais são obrigatórias; não há fallback vazio silencioso. Em produção, substituir
os valores de exemplo por segredos fornecidos pelo ambiente, sem versioná-los.

### PostgreSQL

| Variável | Propriedade da aplicação | Uso | Origem/default |
|---|---|---|---|
| `POSTGRES_HOST` | `spring.datasource.url` | Host do banco | `localhost` |
| `POSTGRES_PORT` | `spring.datasource.url` | Porta do banco | `5432` |
| `POSTGRES_DB` | `spring.datasource.url` | Banco do DocFlow | obrigatório |
| `POSTGRES_USER` | `spring.datasource.username` | Usuário do banco | obrigatório |
| `POSTGRES_PASSWORD` | `spring.datasource.password` | Senha do banco | obrigatório |

### RabbitMQ

| Variável | Propriedade da aplicação | Uso | Origem/default |
|---|---|---|---|
| `RABBITMQ_HOST` | `spring.rabbitmq.host` | Host do broker | `localhost` |
| `RABBITMQ_PORT` | `spring.rabbitmq.port` | Porta AMQP | `5672` |
| `RABBITMQ_USER` | `spring.rabbitmq.username` | Usuário do broker | obrigatório |
| `RABBITMQ_PASSWORD` | `spring.rabbitmq.password` | Senha do broker | obrigatório |
| `RABBITMQ_MANAGEMENT_PORT` | bootstrap | Porta HTTP usada para validar credenciais | `15672` |
O contrato operacional é um consumidor, `prefetch=1` e concorrência `1..1`. Publisher

Esses valores são declarados no código e na configuração versionada; não são
substituíveis por variáveis de ambiente. Publisher confirm, retorno obrigatório e
mensagens persistentes também são propriedades fixas da aplicação.

### MinIO

| Variável | Propriedade/uso | Finalidade | Origem/default |
|---|---|---|---|
| `MINIO_ROOT_USER` | Compose/bootstrap | Usuário administrativo do servidor local | obrigatório no ambiente local |
| `MINIO_ROOT_PASSWORD` | Compose/bootstrap | Senha administrativa do servidor local | obrigatório no ambiente local |
| `MINIO_ENDPOINT` | `docflow.storage.minio.endpoint` | Endpoint acessado pela aplicação | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | `docflow.storage.minio.access-key` | Credencial da aplicação | obrigatório |
| `MINIO_SECRET_KEY` | `docflow.storage.minio.secret-key` | Segredo da aplicação | obrigatório |
| `MINIO_STAGING_BUCKET` | `docflow.storage.staging-bucket` | Bucket intermediário | `docflow-staging` |
| `MINIO_BUCKET` | `docflow.storage.bucket` | Bucket final | `docflow-documents` |
| `MINIO_CONNECT_TIMEOUT` | `docflow.storage.minio.connect-timeout` | Timeout de conexão do cliente | `PT3S` |
| `MINIO_READ_TIMEOUT` | `docflow.storage.minio.read-timeout` | Timeout de leitura do cliente | `PT3S` |
| `MINIO_WRITE_TIMEOUT` | `docflow.storage.minio.write-timeout` | Timeout de escrita do cliente | `PT3S` |

O bootstrap usa `MINIO_ROOT_USER` e `MINIO_ROOT_PASSWORD` somente para administrar o
servidor local. Ele cria/atualiza o usuário da aplicação com `MINIO_ACCESS_KEY` e
`MINIO_SECRET_KEY`, limitado aos dois buckets do DocFlow. O backend usa somente as
credenciais da aplicação.

As credenciais root do servidor não são inferidas como credenciais da aplicação. O
cliente deve usar `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY`. O timeout operacional total
do processamento é `DOCFLOW_PROCESSING_TIMEOUT`, com valor aprovado `PT3S`; a aplicação
deve preservá-lo como deadline sem interromper thread à força.

### Políticas do DocFlow

| Variável | Propriedade | Finalidade | Origem/default |
|---|---|---|---|
| `DOCFLOW_MESSAGING_ENABLED` | `docflow.messaging.enabled` | Habilitar mensageria da aplicação | `true` |
| `DOCFLOW_RECONCILIATION_ENABLED` | `docflow.reconciliation.enabled` | Habilitar o scheduler de reconciliação | `true` |
| `DOCFLOW_RECONCILIATION_MAX_ATTEMPTS` | `docflow.reconciliation.max-attempts` | Limite de tentativas | `5` |
| `DOCFLOW_RECONCILIATION_BACKOFF` | `docflow.reconciliation.backoff` | Atrasos em ISO-8601 | `PT1M,PT5M,PT15M,PT30M,PT60M` |
| `DOCFLOW_PROCESSING_TIMEOUT` | `docflow.processing.timeout` | Deadline do processamento | `PT3S` |
| `DOCFLOW_RECONCILIATION_SCHEDULE_INTERVAL` | `docflow.reconciliation.schedule-interval` | Intervalo do scheduler interno | `PT1M` |

O bootstrap também aceita `BOOTSTRAP_MAX_ATTEMPTS` e
`BOOTSTRAP_RETRY_DELAY_SECONDS` para controlar a espera operacional; ambos têm valores
seguros no `.env.example` e não alteram a política de negócio.

O backoff de reconciliação é, em ordem, `1m`, `5m`, `15m`, `30m` e `60m`. A opção
`DOCFLOW_MESSAGING_ENABLED=false` é reservada aos fixtures de teste que usam somente
PostgreSQL; o ambiente local completo deve mantê-la como `true`.

O bucket de staging é separado do bucket final e a chave lógica dos objetos segue
`staging/{documentId}`. Não habilitar acesso público ao bucket.

O scheduler consulta documentos `PROCESSING` elegíveis, reivindica cada linha com claim
atômico PostgreSQL e consulta somente `exists(objectKey)`. Ele não expõe endpoint público
e usa cinco tentativas com backoff de `1m`, `5m`, `15m`, `30m` e `60m`.

O limite funcional aprovado é `52428800` bytes. Antes de disponibilizar o endpoint, a
camada HTTP também precisa aplicar esse limite em sua configuração de upload, por
exemplo com propriedades equivalentes a:

```properties
spring.servlet.multipart.max-file-size=52428800B
spring.servlet.multipart.max-request-size=52428800B
```

Essas propriedades ainda precisam ser confirmadas no ambiente de execução; a validação
do fluxo também rejeita o conteúdo depois da contagem no staging e antes da persistência
e publicação.

## Bootstrap local

Depois de copiar `infra/.env.example` para `infra/.env`, o serviço separado de bootstrap
deve ser executado pelo Compose:

```powershell
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres rabbitmq minio
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
```

Ele aguarda e autentica PostgreSQL, RabbitMQ e MinIO, cria `docflow-staging` e
`docflow-documents` com `--ignore-existing` e provisiona o usuário da aplicação MinIO.
Credencial inválida ou serviço indisponível encerra o bootstrap com mensagem explícita.
Não apagar volumes persistentes automaticamente; credenciais antigas devem ser
diagnosticadas antes de qualquer ação destrutiva.

### Execução a partir de checkout limpo

O caminho reproduzível para outra pessoa é:

```powershell
git status --short
Copy-Item .\infra\.env.example .\infra\.env
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml config --quiet
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml up -d postgres rabbitmq minio
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml run --rm bootstrap
```

Depois, iniciar o backend conforme o README e executar o smoke test abaixo. O arquivo
`infra/.env` é local e não deve ser commitado. Um checkout limpo não significa remover
dados já existentes nos volumes: antes de trocar credenciais, inspecione o estado atual:

```powershell
docker volume ls --filter name=docflow
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml ps
docker compose --env-file .\infra\.env -f .\infra\docker-compose.yml logs --tail=100 postgres rabbitmq minio bootstrap
```

Se o bootstrap falhar por credencial ou serviço indisponível, corrija a causa indicada e
repita o comando. Não execute `docker compose down -v`, não remova volumes e não troque
credenciais apagando os dados como atalho operacional.

## Preparação manual do MinIO (fallback operacional)

Com o MinIO disponível e as credenciais carregadas no ambiente, configurar um alias
usando os valores do próprio ambiente e criar o bucket:

```text
mc alias set docflow http://localhost:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD
mc mb --ignore-existing docflow/docflow-staging
mc ls docflow/docflow-staging
```

O comando não deve ser executado com credenciais literais versionadas. Lifecycle,
retenção e limpeza automática do staging não estão definidos nesta feature.

## Fluxo operacional

1. O cliente envia a parte `file` com filename, `Content-Type` e conteúdo binário.
2. A API valida presença, tipo detectado por Tika, conteúdo vazio e tamanho máximo.
3. O serviço grava o stream em `docflow-staging/staging/{documentId}` e fecha o stream.
4. O documento é persistido como `PENDING` com o tamanho contado.
5. A publicação encaminha somente a referência e os metadados definidos no contrato
   `DocumentStorageRequested`.
6. O processamento final, a remoção do staging e a confirmação do documento pertencem
   à feature de mensageria/armazenamento final.

O endpoint não deve retornar aceitação antes da gravação do staging e da publicação
confirmada.

## Operações de consulta, download e exclusão

Depois que o backend estiver disponível, os contratos da feature de gestão de documentos
podem ser verificados sem acessar diretamente PostgreSQL, RabbitMQ ou MinIO:

```powershell
curl.exe http://localhost:8080/documents
curl.exe http://localhost:8080/documents/{documentId}
curl.exe -OJ http://localhost:8080/documents/{documentId}/content
curl.exe -i -X DELETE http://localhost:8080/documents/{documentId}
```

`GET /documents` retorna todos os estados (`PENDING`, `PROCESSING`, `COMPLETED` e
`FAILED`). O download só retorna `200` para `COMPLETED`; os demais estados retornam
`409/DOCUMENT_CONTENT_NOT_AVAILABLE`. O conteúdo é transmitido em streaming com
`Content-Type`, `Content-Length`, `Content-Disposition: attachment` e
`Cache-Control: no-store`.

O frontend chama `DELETE /documents/{documentId}` somente após confirmação explícita do
usuário. O caso de uso adquire lock da linha, remove o objeto final e o staging
determinístico e só então remove o registro PostgreSQL. Falhas de storage preservam o
registro para nova tentativa e não são apresentadas como sucesso. A política completa,
incluindo mensagens tardias do RabbitMQ, está no
[ADR-006 — Exclusão definitiva de documentos](../specs/02-arquitetura/DECISAO/ADR-006-exclusao-definitiva-documentos.md).

## Mensageria

O contrato lógico usa JSON v1 com `schemaVersion`, `documentId`, `objectKey`,
`contentReference`, `sizeBytes` e `contentType`. A routing key aprovada é
`document.storage.requested`; exchange, fila e mensagens devem ser duráveis/persistentes
conforme a Spec da feature de mensageria.

Os nomes operacionais aprovados são:

- exchange `docflow.document-storage`;
- fila principal `docflow.document-storage.requested`;
- routing key `document.storage.requested`;
- DLX `docflow.document-storage.dlx` e DLQ `docflow.document-storage.dlq`;
- filas de retry `docflow.document-storage.retry.5s`, `.15s` e `.60s`.

As mensagens são persistentes, usam schema `1` e o publisher aguarda confirmação síncrona.
Mensagens inválidas seguem diretamente para a DLQ. Falhas transitórias são reencaminhadas
para as filas de 5, 15 e 60 segundos, totalizando três retries além da entrega inicial.

### Smoke test local

1. Copiar `infra/.env.example` para `infra/.env` e preencher as credenciais sem
   versioná-las.
2. Subir PostgreSQL, MinIO e RabbitMQ com `infra/docker-compose.yml`.
3. Confirmar que o serviço `bootstrap` terminou com sucesso e que os dois buckets existem.
4. Enviar um `POST /documents` multipart com a parte `file`.
5. Confirmar `201 Created`, `Location`, `status: PENDING` e `sizeBytes` real.
6. No RabbitMQ, confirmar a publicação persistente e o consumo da mensagem v1.
7. Verificar que o objeto final existe, que o staging só é removido após sucesso e que
   uma falha de publicação mantém o documento `PENDING` e o staging preservado.
8. Consultar `GET /documents` e confirmar os campos e estados retornados.
9. Para um documento `COMPLETED`, validar o download e seus headers; tentar o download
   de um documento não concluído e confirmar `409`.
10. Excluir um documento pela interface após confirmação e confirmar `204`, remoção do
    item na listagem e ausência dos objetos associados.

O smoke test é considerado reprodutível quando outra pessoa consegue executar os passos
acima usando apenas `infra/.env.example`, este documento e o README, substituindo os
valores locais sem registrar segredos. O resultado deve ser anotado fora do repositório
com data/hora, `documentId`, estado observado e resultado dos checks de buckets e filas.

Quando uma gravação final resultar em `RESULT_UNKNOWN`, o documento permanece
`PROCESSING`; após os retries RabbitMQ, o scheduler continua a reconciliação sem repetir
`store`. `exists(objectKey)` confirmado leva a `COMPLETED`; cinco tentativas sem
confirmação levam a `FAILED`.

Toda falha deve ser correlacionada pelo `documentId`; logs não devem conter conteúdo
binário ou credenciais.

## Rollback

1. Interromper o tráfego para a versão nova e preservar as mensagens já publicadas; não
   purgar a fila nem apagar o bucket inteiro.
2. Reverter a aplicação para a versão anterior pelo mecanismo normal de deploy.
3. Avisar os clientes: a versão anterior aceita o contrato JSON antigo, enquanto a
   versão nova exige multipart.
4. Preservar objetos `staging/{documentId}` e mensagens relacionadas para investigação
   ou reprocessamento conforme a feature de mensageria.
5. Se o staging tiver sido gravado e a publicação falhar, não remover o objeto sem uma
   decisão operacional de compensação.
6. Se a publicação tiver resultado inconclusivo, tratar a mensagem como potencialmente
   existente e depender da idempotência definida pelo consumidor; não publicar cópias
   manualmente sem correlação por `documentId`.
7. Se a persistência falhar depois do staging, registrar o `documentId` e manter o
   objeto para investigação; qualquer limpeza deve ser seletiva e aprovada.

O rollback não inclui edição direta no PostgreSQL, remoção em massa no MinIO ou alteração
manual de mensagens. A decisão de limpeza/reconciliação deve ser registrada antes de
qualquer ação destrutiva.
