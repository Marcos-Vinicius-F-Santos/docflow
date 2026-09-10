# Observability Spec — Armazenamento binário de documentos

Owner: Engineering  
Related SLOs: A definir antes do release

## 1. Critical User Journeys

- Registrar documento com metadados e conteúdo binário.
- Confirmar que o conteúdo foi armazenado e associado ao documento.
- Consultar um documento em `PENDING`, `PROCESSING`, `COMPLETED` ou `FAILED`.
- Recuperar uma operação com falha parcial ou resultado inconclusivo.

## 2. SLIs

### Availability

Definição: percentual de operações de registro que recebem uma resposta válida do serviço, excluindo rejeições de entrada, com PostgreSQL e MinIO disponíveis.

### Latency

Definição: tempo entre o recebimento do upload e a resposta que informa sucesso, falha conhecida ou resultado não confirmado, segmentado por tamanho do conteúdo.

### Correctness

Definição: percentual de operações reportadas como sucesso que possuem conteúdo confirmado e referência persistida, com bytes e metadados coerentes.

## 3. SLOs

| SLI | Target | Window |
|---|---:|---|
| Availability | TBD | TBD |
| Latency | TBD | TBD |
| Correctness | 100% de sucessos sem divergência | TBD |
| Orphaned content | 0 como estado aceitável | TBD |

Metas quantitativas de disponibilidade, latência, capacidade e taxa de sucesso devem ser aprovadas antes do release.

## 4. Golden Signals

### Latency

- Latência total do registro.
- Latência da escrita no MinIO.
- Latência de finalização no PostgreSQL.

### Traffic

- Número de tentativas.
- Número de bytes recebidos e armazenados.
- Operações simultâneas.

### Errors

- Erros de validação.
- Tamanho inconsistente.
- MinIO indisponível ou rejeitando.
- Resultado inconclusivo.
- Falha de finalização no PostgreSQL.
- Conflitos de concorrência.

### Saturation

- Memória e conexões do backend.
- Capacidade e latência do PostgreSQL.
- Capacidade e latência do MinIO.
- Estados `PROCESSING` acima do limite operacional.

## 5. Metrics

| Metric | Type | Labels | Purpose |
|---|---|---|---|
| `document_storage_attempts_total` | Counter | resultado, provider | Volume de tentativas |
| `document_storage_success_total` | Counter | provider | Sucessos confirmados |
| `document_storage_failures_total` | Counter | categoria, retryable | Falhas classificadas |
| `document_storage_unknown_total` | Counter | etapa | Resultados inconclusivos |
| `document_storage_duration_seconds` | Histogram | resultado, tamanho_bucket | Latência |
| `document_storage_bytes_total` | Counter | resultado | Volume sem conteúdo |
| `document_storage_reconciliation_total` | Counter | resultado | Operações de recuperação |
| `document_storage_orphans_total` | Gauge/Counter | origem | Conteúdo órfão detectado |
| `document_processing_state` | Gauge | status | Documentos por estado |

Labels não podem conter filename, conteúdo, credential, token ou valores arbitrários de alta cardinalidade.

## 6. Logs

Registrar de forma estruturada:

- document ID;
- correlation/operation ID;
- etapa do fluxo;
- resultado;
- categoria do erro;
- retryable;
- duração;
- quantidade de bytes, quando necessária para operação.

Redigir conteúdo binário, payload completo, credenciais, tokens e detalhes internos desnecessários do provider. O retention period deve seguir a política do ambiente.

## 7. Traces

Criar spans ou equivalentes para:

- recebimento e validação da API;
- persistência/transição no PostgreSQL;
- chamada ao `DocumentStorage`;
- operação MinIO;
- finalização da referência e do estado.

Propagar correlation ID sem incluir conteúdo ou secrets nos atributos.

## 8. Dashboards

Dashboard da feature deve conter:

- taxa de sucesso e falha;
- latência total e do MinIO;
- volume de bytes;
- erros por categoria;
- disponibilidade do PostgreSQL/MinIO;
- quantidade e idade de documentos em `PROCESSING`;
- conteúdo órfão e reconciliações.

## 9. Alerts

### ALERT-001 — Storage failure rate elevated

Condition: taxa de falha acima do limite aprovado para a janela operacional.  
Window: definida no release plan.  
Severity: High  
Owner: Engineering/Operations  
Runbook: `specs/09-operations/document-upload/runbook.md`

### ALERT-002 — Object storage unavailable

Condition: health/readiness do MinIO ou bucket essencial indisponível.  
Window: definida no release plan.  
Severity: High  
Owner: Engineering/Operations  
Runbook: `specs/09-operations/document-upload/runbook.md`

### ALERT-003 — Documents stuck in processing

Condition: documentos em `PROCESSING` acima do limite de reconciliação.  
Window: definida no release plan.  
Severity: High  
Owner: Document service owner  
Runbook: `specs/09-operations/document-upload/runbook.md`

### ALERT-004 — Orphaned content detected

Condition: qualquer conteúdo órfão confirmado ou compensação malsucedida.  
Window: imediata após detecção.  
Severity: High  
Owner: Document service owner  
Runbook: `specs/09-operations/document-upload/runbook.md`

## 10. Business / Product Signals

- Percentual de operações reportadas como sucesso com associação persistida.
- Quantidade de documentos em cada estado.
- Taxa de rejeição por metadata/content mismatch.
- Tempo até armazenamento concluído.
- Quantidade de tentativas repetidas.

## 11. Validation After Deploy

- Confirmar health/readiness do PostgreSQL, MinIO e bucket.
- Executar um upload controlado e validar bytes, estado e referência.
- Confirmar métricas, logs e trace correlacionados.
- Confirmar que falha do MinIO gera alerta e não gera sucesso.
- Observar latência, erro, saturação, `PROCESSING` e órfãos durante cada estágio do rollout.
