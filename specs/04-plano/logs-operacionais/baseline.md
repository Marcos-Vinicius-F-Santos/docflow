# Baseline — Logs operacionais

## Situação atual

- O backend usa o logger padrão do Spring/SLF4J e escreve no processo local; não há
  contrato estruturado próprio nem persistência consultável de logs.
- RabbitMQ já possui topologias separadas para storage, notificações e e-mail, mas não
  existe exchange/fila de logs operacionais.
- O fluxo de documentos registra sucessos, warnings e falhas em pontos distintos de
  `DocumentService`, consumers de storage, exclusão, reconciliação e notificações.
- O frontend possui Hub e notificações WebSocket, mas não possui rota `/logs` nem acesso
  a registros operacionais.
- PostgreSQL está na migration V6 e não possui tabela `application_logs`.
- O fallback atual durante indisponibilidade de RabbitMQ é o logger local do processo;
  não existe publisher de logs nem consumer de persistência.

## Pontos a preservar

- Falha de logging não pode alterar status, contador de falhas, retry, staging, storage
  ou exclusão do documento.
- O Hub continua usando REST/WebSocket e não acessa RabbitMQ ou PostgreSQL diretamente.
- Migrations V1–V6 não serão alteradas; a tabela de logs usará a próxima migration.
- O provider de e-mail e as notificações de documentos permanecem independentes dos logs.

## Lacunas verificadas

- Não há API `GET /logs`, filtros, paginação ou ProblemDetail específico.
- Não há retenção automática de registros de log.
- Não há sanitização centralizada para impedir segredo, token, conteúdo binário ou stack
  trace não filtrado no evento persistido.
