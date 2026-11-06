# Execução — Fase 3: observabilidade real

Ambiente-alvo: staging cloud ou ambiente equivalente aprovado.  
Status: correção operacional do CI aplicada; validação externa e exercício de alertas pendentes.

## Pré-condições

- [x] Ambiente da Fase 2 disponível.
- [x] Endpoint de métricas protegido conforme decisão de segurança.
- [ ] Grafana/Prometheus configurados no ambiente externo.
- [x] Limiares e canais de alerta registrados.
- [x] Runbook inicial revisado.
- [x] CI atualizado para usar S3Mock nos testes automatizados de storage.

## Passos

1. Promover o commit aprovado e verificar health.
2. Gerar tráfego de upload, processamento, retry e consulta.
3. Confirmar séries no Prometheus e painéis no Grafana.
4. Provocar uma falha controlada de storage, DLQ ou health conforme o cenário aprovado.
5. Confirmar alerta, correlation ID, logs sanitizados e passos do runbook.
6. Recuperar a dependência e confirmar retorno ao estado normal.
7. Exportar dashboard e salvar logs/evidências sem segredos.

### Validação operacional do CI

1. Acompanhar a execução do workflow após a troca da imagem.
2. Confirmar que Backend/Testcontainers conclui sem retry contra o Quay.
3. Preservar o teste MinIO real como validação manual/opcional, fora do gate obrigatório.

## Rollback

Desativar a configuração de métricas/alertas ou retornar ao digest anterior se houver
impacto no fluxo principal. Não apagar séries ou evidências da ocorrência provocada.

## Resultado obrigatório

Dashboard/export, métricas, alerta provocado, runbook exercitado e checklist aprovado.

