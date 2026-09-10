# Runbook — Armazenamento binário de documentos

Owner: Engineering/Operations  
Service: DocFlow document service  
Criticality: High  
Last reviewed: 2026-09-08

## Overview

O fluxo armazena bytes no MinIO e metadados, estado e referência no PostgreSQL. `COMPLETED` significa que o storage foi confirmado e a referência foi persistida.

O serviço não usa transação distribuída. Falhas parciais podem exigir retry, compensação ou reconciliação.

## Dependencies

- Backend DocFlow.
- PostgreSQL.
- MinIO e bucket privado de documentos.
- Configuração/secrets do ambiente.
- Dashboards, alertas e correlação de logs/traces.

## Dashboards

Usar o dashboard de armazenamento de documentos para verificar:

- sucesso/falha/resultado inconclusivo;
- latência;
- disponibilidade das dependências;
- bytes processados;
- documentos em `PROCESSING`;
- órfãos e reconciliações.

## Logs

Filtrar por document ID, correlation ID, operation ID e categoria de erro. Não solicitar ou registrar conteúdo binário, tokens ou credenciais.

## Traces

Verificar a sequência API → PostgreSQL → DocumentStorage → MinIO → PostgreSQL. Comparar latência e erro de cada boundary.

## Health Checks

1. Verificar health/readiness do backend.
2. Verificar conectividade e autenticação do PostgreSQL.
3. Verificar conectividade do MinIO.
4. Verificar existência e privacidade do bucket.
5. Confirmar que as credenciais usadas são as do ambiente correto.

## Common Incidents

### INC-001 — High Error Rate

Symptoms:

- Aumento de `document_storage_failures_total`.
- Uploads retornando falha ou resultado inconclusivo.
- Crescimento de documentos em `FAILED` ou `PROCESSING`.

Likely causes:

- MinIO indisponível ou lento.
- Bucket ausente ou sem permissão.
- Credenciais inválidas.
- Limite de tamanho ou recurso excedido.

Diagnosis:

1. Consultar alertas e categoria de erro.
2. Verificar health/readiness e logs correlacionados.
3. Testar acesso controlado ao bucket sem expor credentials.
4. Verificar saturação de backend, PostgreSQL e MinIO.

Mitigation:

1. Interromper ou reduzir rollout de uploads.
2. Corrigir disponibilidade, permissão ou configuração no ambiente.
3. Não marcar documentos como concluídos manualmente sem confirmação do objeto.
4. Reprocessar somente após a dependência estar saudável.

Recovery verification:

- Novo upload controlado conclui com bytes, referência e estado coerentes.
- Taxa de erro retorna ao limite aprovado.
- Não há crescimento de órfãos ou `PROCESSING` preso.

Escalation: owner do serviço → infraestrutura/storage → segurança se houver exposição.

### INC-002 — Dependency Failure

Symptoms:

- Health/readiness de PostgreSQL ou MinIO falha.
- Timeouts ou recusas de conexão.

Diagnosis:

1. Verificar status da dependência e rede.
2. Verificar endpoint, bucket e secrets do ambiente.
3. Correlacionar horário da falha com deploy ou alteração de configuração.

Mitigation:

1. Pausar rollout.
2. Restaurar dependência ou configuração aprovada.
3. Manter respostas de falha sem declarar sucesso.

### INC-003 — Object Stored but Document Not Finalized

Symptoms:

- MinIO registra escrita, mas documento não está `COMPLETED`.
- Existe documento em `PROCESSING` ou falha de finalização no banco.

Diagnosis:

1. Localizar document ID e operation/correlation ID.
2. Determinar se a escrita no MinIO foi confirmada.
3. Verificar existência do objeto pela referência estável, sem expor conteúdo.
4. Verificar estado e versão do documento no PostgreSQL.

Mitigation:

1. Executar retry/reconciliação usando a mesma referência.
2. Se a associação puder ser finalizada com segurança, persistir estado conforme o procedimento aprovado.
3. Se o objeto estiver órfão, executar compensação aprovada ou registrar ação de recuperação.
4. Abrir incidente se a reconciliação falhar.

Recovery verification:

- Não há documento reportado como sucesso sem referência.
- O objeto possui uma associação única ou foi compensado.
- Métricas de órfãos e `PROCESSING` retornam ao normal.

### INC-004 — Document Stuck in PROCESSING

Symptoms:

- Documento excede o limite de reconciliação.
- Resultado do upload permanece desconhecido.

Diagnosis:

1. Consultar logs/traces pelo document ID.
2. Verificar se MinIO recebeu a operação.
3. Verificar versão/estado no PostgreSQL.
4. Identificar se existe retry concorrente.

Mitigation:

1. Bloquear nova operação concorrente para o mesmo documento.
2. Reexecutar verificação/retry com referência estável.
3. Marcar `FAILED` somente quando a falha for conhecida.
4. Escalar conteúdo órfão ou resultado não determinável.

## Rollback

- Pausar o rollout e desabilitar novas operações conforme o release plan.
- Reverter aplicação somente com compatibilidade para `object_key` e `version`.
- Não remover objetos automaticamente durante rollback.
- Executar reconciliação/forward recovery conforme migration e estado de cada documento.

## Feature Flags

Nenhuma flag é definida pela Feature Spec. O release plan deve registrar a estratégia de habilitação progressiva ou declarar explicitamente a ausência de flag.

## Data Recovery

- PostgreSQL é a fonte de verdade para metadados, estado e referência.
- MinIO é a fonte de verdade para bytes confirmados.
- Nunca reconstruir conteúdo a partir de logs.
- Não apagar objetos sem confirmar associação, retenção e autorização operacional.
- Registrar toda compensação ou reconciliação com document ID e correlation ID.

## Escalation

1. Owner do serviço DocFlow.
2. Owner de PostgreSQL/infraestrutura.
3. Owner de MinIO/object storage.
4. Segurança em caso de credencial, acesso indevido ou exposição de conteúdo.
