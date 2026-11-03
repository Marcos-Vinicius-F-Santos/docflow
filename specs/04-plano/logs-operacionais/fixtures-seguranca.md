# Fixtures e segurança de logs

Os testes devem cobrir:

- evento INFO, WARN e ERROR com e sem `documentId`/`correlationId`;
- mensagem e `details` contendo tentativas de segredo, token, API key, senha, bucket,
  conteúdo binário e stack trace;
- evento inválido e evento duplicado;
- RabbitMQ indisponível com fallback no logger local;
- falha do consumer sem republicação recursiva.

Nenhum fixture usa segredo real, arquivo binário ou chamada externa.
