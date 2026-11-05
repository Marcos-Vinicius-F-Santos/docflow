# Runbook — Fase 3: observabilidade

## Sinais

- Health: `https://docflow-backend-g2e2.onrender.com/actuator/health`
- Métricas protegidas: `https://docflow-backend-g2e2.onrender.com/actuator/prometheus`
- Dashboard: Grafana Cloud, dashboard `DocFlow - Operação`.
- Logs: Render → `docflow-backend` → Logs.

## Primeiro diagnóstico

1. Confirmar se o alerta é `firing` ou `resolved` no Grafana.
2. Consultar o painel de estado dos documentos e o painel de erro da dependência.
3. Usar o `X-Correlation-Id` da requisição ou evento para localizar os logs sem
   pesquisar por nome de arquivo ou conteúdo binário.
4. Confirmar o estado do Neon, RabbitMQ e Cloudflare R2 nos respectivos painéis.

## DLQ

1. Verificar `docflow_rabbit_messages_total{outcome="dead_letter"}`.
2. Conferir os logs do consumidor no Render.
3. Identificar se a falha é de validação permanente ou dependência indisponível.
4. Corrigir a dependência antes de executar retry manual.
5. Registrar a ocorrência e a correlação usada.

## PROCESSING / RESULT_UNKNOWN

1. Verificar `docflow_documents_result_unknown` e a duração de processamento.
2. Consultar storage e RabbitMQ.
3. Confirmar se o objeto final existe no bucket `docflow-documents`.
4. Executar retry somente depois de confirmar que a operação é segura.

## Storage ou health degradado

1. Confirmar as variáveis `MINIO_ENDPOINT`, bucket e credenciais no Render.
2. Testar o health do backend.
3. Validar disponibilidade e permissões dos buckets no Cloudflare R2.
4. Não reportar sucesso ao usuário enquanto a dependência estiver indisponível.
5. Após recuperar a dependência, confirmar que as métricas retornaram ao normal.

## Rollback

Desativar `DOCFLOW_PROMETHEUS_ENABLED` ou retornar ao digest anterior do serviço
caso a instrumentação impacte o fluxo principal. Não apagar métricas, alertas ou
evidências da ocorrência.
