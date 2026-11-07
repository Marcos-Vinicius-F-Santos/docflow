# Fase 5 — preflight operacional

Status: carga, resiliência, métricas temporais e contrato protegido executados no
staging kind. O teste autenticado contra o Render fica opcional/manual por exigir um
JWT real de usuário Neon.

## Registro atual

- Ferramenta escolhida: k6.
- Versão validada: `v2.3.0`.
- Binário: `tools/k6/k6-v2.3.0-windows-amd64/k6.exe` (ignorado pelo Git).
- Scripts preparados: smoke protegido/health e upload concorrente parametrizado.
- Ambiente-alvo: cluster kind `docflow-staging`, namespace `docflow-staging`.
- Produção: não executar.

## Resultado medido — 2026-11-06

- Smoke health: passou; `1` request, `0%` erro, P95 `22,61 ms`.
- Carga health: passou com `2` VUs por `1m`.
- Requests: `67.567`; taxa observada `1.126,1 req/s`.
- P50: `1,56 ms`; P95: `2,54 ms`; máximo: `29,1 ms`.
- Erros HTTP: `0%`; checks: `135.134/135.134` passaram.
- Escopo: somente `/actuator/health`; este resultado não representa upload ou
  processamento de documentos.
- Upload concorrente: passou com `2` VUs por `30s`, fixture pequeno e limpeza automática.
- Uploads aceitos: `13`; falhas: `0`; requests HTTP: `100`; checks: `39/39`.
- Upload P50: `49 ms`; P95: `1,23 s`; máximo: `1,23 s`.
- Limpeza: todos os documentos de teste foram removidos; listagem final retornou `0`.
- Observação: o staging foi validado nos dois modos: sem proteção para os cenários
  históricos e com proteção habilitada temporariamente usando JWKS efêmero, sem
  dependência paga. No modo protegido, `GET /documents` respondeu `200` e o upload
  autenticado respondeu `201`, seguido de limpeza `204`.
- Documento grande: PDF válido de `1 MiB`, `2` VUs por `20s`, passou com `10` uploads,
  `0%` erro e `0` falhas de upload.
- Documento grande P50: `3,25 ms`; P95: `45,21 ms`; P99: `74,76 ms`.
- Requests HTTP no cenário grande: `70`; dados enviados: `11 MiB`; limpeza final: `0`
  documentos listados.
- Um fixture de bytes nulos foi rejeitado com `400 DOCUMENT_CONTENT_TYPE_UNKNOWN`; o
  cenário válido foi repetido usando assinatura PDF e content type coerente.

## Amostra temporal adicional — 2026-11-07

- Health-load: `2` VUs por `20s`, `10.867` requests, `21.734` checks, `100%` de
  sucesso e `0%` de erro HTTP.
- Latência observada: P95 `6,82 ms`; máximo `31,95 ms`; taxa `543,31 req/s`.
- Amostras `kubectl top` coletadas durante a carga: backend `3–51m CPU` e
  `321–324Mi`, RabbitMQ `636–906m CPU` e `144–197Mi`, Postgres `22–38m CPU` e
  `63Mi`, MinIO `1m CPU` e `83Mi`.
- O cenário protegido autenticado também passou: `GET /documents=200`, upload
  `201`, limpeza `204`, todos os checks k6 aprovados e sem falhas HTTP.

## Falhas controladas executadas

- RabbitMQ reduzido a zero réplicas: upload respondeu `503 DOCUMENT_RETRY_UNAVAILABLE`;
  RabbitMQ foi restaurado e ficou `Ready`.
- MinIO reduzido a zero réplicas durante upload: operação respondeu `500` sem sucesso
  falso; MinIO foi restaurado e ficou `Ready`.
- MinIO interrompido durante processamento de documento aceito: documento permaneceu
  `PROCESSING`, registrou falha transitória, retry, `RESULT_UNKNOWN` e depois concluiu
  `COMPLETED` após a restauração. O log registrou a sequência de recuperação.
- Backend reiniciado durante processamento: rollout concluído, documento terminou em
  `COMPLETED` e foi removido com `204`.
- Limpeza final: o documento PENDING deixado pelo cenário RabbitMQ foi identificado e
  removido explicitamente; a listagem final ficou vazia.

## Validação automatizada adicional

- Maven: suíte completa com `256` testes, `0` falhas, `0` erros e `0` ignorados.
- Maven: também foram confirmados `26` testes selecionados de reconciliação,
  concorrência e consumidores RabbitMQ; `0` falhas, `0` erros, build successful.
- Duplicidade/idempotência: validada na suíte automatizada; cenário de duplicidade
  externa também foi executado: RabbitMQ confirmou a publicação duplicada, o documento
  permaneceu `COMPLETED`, `failureCount=0` e não houve segundo registro.

## Métricas operacionais complementares

- O Metrics Server oficial `v0.9.1` está instalado no kind, com resolução de `15s`.
  O uso de `--kubelet-insecure-tls` é restrito ao cluster local de teste.
- Durante carga health de `2` VUs por `20s`, foram coletadas amostras temporais:
  backend `3–51m CPU` e `321–324Mi`, RabbitMQ `636–906m CPU` e `144–197Mi`,
  Postgres `22–38m CPU` e `63Mi`, MinIO `1m CPU` e `83Mi`.
- Snapshots de pico cgroup após os testes: backend `341.135.360` bytes, RabbitMQ
  `240.836.608` bytes e MinIO `95.555.584` bytes. Esses valores são picos desde o
  início dos pods, não devem ser apresentados como pico exclusivo da carga k6.
- Filas de storage estavam zeradas após a limpeza. A fila preexistente
  `docflow.document-email-notifications.dlq` tinha `90` mensagens; não foi alterada
  porque não foi possível atribuí-la aos dados desta execução.

## Limitação residual não paga

- A execução autenticada no Render ainda requer um JWT real emitido para um usuário
  Neon. O navegador remoto ficou offline e o fluxo de criação do Neon exige
  verificação de e-mail; isso impede somente a prova cloud end-to-end nesta sessão.
  A mecânica de proteção, JWKS e upload já foi validada localmente sem serviço pago.

## Encerramento técnico

- Scripts, fixtures, métricas e relatório estão versionados no repositório.
- Resta apenas o commit/aprovação da fase e, se desejado, a repetição manual no
  Render com uma sessão Neon real.
