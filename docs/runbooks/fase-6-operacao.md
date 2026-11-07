# Runbook operacional — Fase 6

Este runbook usa somente alvos explicitamente identificados. Nunca executar limpeza
em um namespace, banco, bucket ou volume sem confirmar o nome e o ambiente.

## Diagnóstico inicial

```powershell
kubectl --context kind-docflow-staging -n docflow-staging get pods,events
kubectl --context kind-docflow-staging -n docflow-staging get deploy docflow-backend -o wide
kubectl --context kind-docflow-staging top pods
curl.exe -i http://localhost:8080/actuator/health/liveness
curl.exe -i http://localhost:8080/actuator/health/readiness
```

O liveness deve continuar `200` quando uma dependência estiver indisponível. O
readiness pode responder `503` quando PostgreSQL ou RabbitMQ não estiverem prontos.
Falhas do MinIO aparecem nas operações de documento e não retiram o processo do
balanceamento.

## API, banco, RabbitMQ e storage

1. Confirmar o `documentId` e o `X-Correlation-Id` nos logs.
2. Verificar `kubectl logs deployment/docflow-backend` sem expor tokens ou conteúdo.
3. Para RabbitMQ, verificar filas principais e DLQs; não consumir nem apagar mensagens
   sem registrar a fila e o motivo.
4. Para MinIO, verificar os buckets de staging/final e o estado do documento.
5. Restaurar a dependência e aguardar o retry/reconciliador antes de repetir o upload.

## Backup PostgreSQL

O backup local é custom-format e recebe nome identificável:

```powershell
./scripts/postgres-backup.ps1 -OutputPath .\artifacts\docflow-$(Get-Date -Format yyyyMMdd-HHmmss).dump
```

O arquivo deve ser mantido fora do Git e sua existência deve ser confirmada antes de
qualquer restauração.

## Restore PostgreSQL

Restaurar somente em banco separado e identificado:

```powershell
./scripts/postgres-restore.ps1 -InputPath .\artifacts\docflow-test.dump -TargetDatabase docflow_restore_test -ConfirmRestore
```

O script recusa o banco de produção/local padrão sem `-ConfirmRestore` e não altera o
banco original. Depois do restore, validar migrations, contagem de documentos, estados
e uma leitura da aplicação. O restore não recria objetos MinIO; validar os buckets
separadamente.

## DLQ, retenção e órfãos

- DLQs são evidência operacional; primeiro exportar/inspecionar uma amostra segura.
- A retenção de logs é de três horas e não remove documentos nem objetos.
- Exclusão de documento remove metadados e objetos por ordem segura, conforme ADR-006.
- Não existe limpeza automática de objetos órfãos nesta fase. Qualquer limpeza futura
  deve receber uma lista explícita de chaves, ter backup e permitir dry-run.

## Rollback

1. Parar a promoção da nova imagem.
2. Identificar a imagem anterior por digest/tag.
3. Reverter somente o Deployment do backend.
4. Manter migrations expand-only aplicadas e validar `/actuator/health/readiness`.
5. Preservar banco, buckets, filas e artefatos para investigação.

## Segurança

Não registrar JWT, cookies, chaves, senhas, conteúdo binário, `objectKey` ou nomes de
bucket em tickets, logs de shell ou evidências públicas.
