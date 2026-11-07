# Fase 5 — falhas controladas

Este procedimento é um roteiro operacional. Não executar em Render, Neon de produção,
ou qualquer ambiente que contenha dados reais.

Pré-condições: smoke verde, observabilidade aberta, janela aprovada, backup/rollback
conhecido, e identificação dos recursos do cluster `kind-docflow-staging`.

Executar somente um cenário por vez e restaurar o recurso antes do próximo:

1. RabbitMQ indisponível: interromper o deployment de RabbitMQ no staging, registrar
   mensagens pendentes/retries, restaurar, e confirmar consumo sem duplicação indevida.
2. Storage indisponível: interromper o serviço MinIO do staging, registrar falhas de
   upload/processamento, restaurar, e confirmar retry/reconciliação.
3. Reinício do backend: reiniciar somente o deployment do backend, confirmar health,
   consumidores, documentos `PENDING`/`PROCESSING` e retomada.
4. Mensagem duplicada: reenviar uma mensagem identificada em staging, confirmar que a
   chave/idempotência impede processamento duplicado e que o estado final permanece único.
5. `RESULT_UNKNOWN`: produzir o estado somente com a ferramenta/procedimento já
   documentado no staging, confirmar reconciliação e registrar a evidência.

Para cada cenário registrar commit, imagem, horário, comando, métricas, IDs de teste,
resultado, recuperação e limpeza. Se ocorrer erro, saturação ou custo inesperado,
parar os geradores antes de qualquer nova ação.
