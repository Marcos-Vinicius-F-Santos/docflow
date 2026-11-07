# Fase 5 — k6

Os scripts desta pasta são executados somente contra um backend de staging isolado.
Eles não possuem token, senha, URL de produção ou credencial versionada.

## Ferramenta

- Ferramenta aprovada: k6.
- Versão validada localmente: `v2.3.0`.
- O binário local fica em `tools/k6/`, que é ignorado pelo Git.

## Smoke sem criar dados

PowerShell:

```powershell
$env:K6 = (Resolve-Path .\tools\k6\k6-v2.3.0-windows-amd64\k6.exe).Path
$env:DOCFLOW_BASE_URL = 'http://<backend-staging>'
$env:DOCFLOW_ACCESS_TOKEN = '<token-temporario-de-staging>'
& $env:K6 run .\ops\load\k6\smoke.js
```

Para verificar somente health, use `K6_MODE=health` e não informe token.

Para a carga conservadora de infraestrutura, `K6_MODE=health-load` usa 2 VUs por
1 minuto por padrão e também não exige token:

```powershell
& $env:K6 run --env DOCFLOW_BASE_URL=http://<backend-staging> --env K6_MODE=health-load .\ops\load\k6\smoke.js
```

## Upload concorrente

O script exige `K6_APPROVED=YES` para impedir execução acidental antes da aprovação
de concorrência, duração, tamanho da massa e limites de custo/erro.

```powershell
$env:K6_APPROVED = 'YES'
$env:K6_VUS = '<concorrencia-aprovada>'
$env:K6_DURATION = '<duracao-aprovada>'
$env:DOCFLOW_FIXTURE = (Resolve-Path .\ops\load\k6\fixtures\k6-smoke.txt).Path
& $env:K6 run .\ops\load\k6\upload.js
```

`K6_CLEANUP` é `false` por padrão. Só deve ser `true` quando a API e a política
de limpeza de staging tiverem sido confirmadas. O relatório deve registrar os IDs
ou o critério usado para limpar os documentos de teste.

Para um fixture PDF, informe também `DOCFLOW_FILENAME=k6-large.pdf` e
`DOCFLOW_CONTENT_TYPE=application/pdf`; o nome e o tipo precisam corresponder ao
conteúdo para que a validação de assinatura do backend seja exercitada corretamente.

O staging kind atual está sem configuração de autenticação e respondeu `200` a
`GET /documents`. Para esse ambiente local somente, pode-se usar
`K6_REQUIRE_AUTH=false`; o padrão continua exigindo JWT e não deve ser alterado
para ambientes cloud.

## Documento grande

O fixture grande deve ser criado fora do Git, dentro do staging de teste, e informado
em `DOCFLOW_FIXTURE`. O tamanho efetivo precisa ser registrado no relatório; não usar
um arquivo grande contra o Render de produção.

## Métricas e limites

O k6 produz `http_req_duration` com P50/P95/P99 no resumo. O upload também registra
`docflow_upload_duration` e `docflow_upload_failures`. Memória, fila RabbitMQ, retries,
DLQ e estado do documento precisam ser coletados no Grafana durante a execução.

Falhas controladas de RabbitMQ, storage, reinício e reconciliação não são acionadas
automaticamente por estes scripts: devem ser executadas uma por vez no cluster de
staging, seguindo `controlled-failures.md`.
