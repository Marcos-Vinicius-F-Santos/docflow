# Baseline de verificação — Hub de documentos

Data: 2026-10-27  
Tarefa: T-006  
Objetivo: confirmar que os fluxos existentes permanecem verdes antes da lógica de
retry, contagem de falhas e exclusão atrasada.

## Comandos executados

| Comando | Resultado |
|---|---|
| `mvn test` em `backend/` | PASS — 118 testes, 0 falhas, 0 erros, 0 ignorados. |
| `npm test -- --watch=false` em `frontend/` | PASS — 6 arquivos, 61 testes, 0 falhas. |
| Flyway durante `mvn test` | PASS — migrations V1–V6 validadas e V6 aplicada após V5 em banco PostgreSQL Testcontainers. |

O backend usou Testcontainers com PostgreSQL, RabbitMQ e MinIO. O frontend foi
executado com o runner Vitest configurado pelo Angular.

## Fluxos cobertos pela baseline

- upload multipart existente e validação do arquivo;
- consulta individual e polling de `PENDING`/`PROCESSING`;
- listagem com os quatro estados públicos;
- download individual de conteúdo concluído;
- exclusão individual com confirmação no frontend;
- contratos atuais de publisher/consumidor, retries e reconciliação já existentes;
- inicialização da aplicação e aplicação das migrations V1–V6.

## Limites desta baseline

Retry manual do Hub, contador `falhas`, `isExcluded`, `exclusionScheduledAt`, fila de
exclusão e página Hub ainda não possuem implementação nem testes próprios. Eles são
lacunas intencionais e permanecem para T-007 em diante. A baseline não constitui
aprovação da implementação da feature nem marca qualquer tarefa como concluída.
