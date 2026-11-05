# Staging cloud free tier

Esta configuração é destinada exclusivamente a portfólio e estudo. Ela não é uma
topologia de produção e deve permanecer dentro dos limites gratuitos dos provedores.

## Componentes

| Componente | Serviço | Configuração no DocFlow |
|---|---|---|
| Frontend | Render Static Site Free | `render.yaml` |
| Backend | Render Web Service Free | `render.yaml` e `backend/Dockerfile` |
| PostgreSQL | Neon Free | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| RabbitMQ | CloudAMQP Little Lemur | `SPRING_RABBITMQ_ADDRESSES` |
| Objetos | Cloudflare R2 | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` |

O cliente MinIO existente é usado contra a API S3-compatible do R2. Os nomes das
variáveis `MINIO_*` são mantidos para preservar o adaptador provider-neutral.

## Configuração externa

1. Criar os dois serviços do `render.yaml` a partir do repositório GitHub.
2. Criar o banco PostgreSQL no Neon e informar a URL como JDBC:
   `jdbc:postgresql://<host>/<database>?sslmode=require`.
3. Criar o broker RabbitMQ no CloudAMQP e informar a URI AMQPS completa em
   `SPRING_RABBITMQ_ADDRESSES`.
4. Criar no R2 os buckets `docflow-staging` e `docflow-documents`, gerar uma chave
   limitada ao acesso desses buckets e informar o endpoint S3 da conta.
5. Depois que o Render gerar a URL do frontend, usar a mesma URL em
   `DOCFLOW_CORS_ALLOWED_ORIGINS` e `DOCFLOW_NOTIFICATION_WEBSOCKET_ALLOWED_ORIGINS`.
6. Usar a URL do backend em `DOCFLOW_API_BASE_URL` no serviço estático.

Não registrar valores dos secrets no repositório, em screenshots, logs ou issues.
O Flyway executa na inicialização do backend usando a conexão JDBC configurada.

## Limitações esperadas

- o Web Service Free pode dormir quando fica ocioso;
- o filesystem do backend é efêmero;
- banco, broker e objetos devem permanecer nos serviços externos;
- cold start, limites de uso e ausência de alta disponibilidade são aceitáveis para
  este ambiente de estudo.
