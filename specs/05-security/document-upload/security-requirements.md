# Security Requirements — Armazenamento binário de documentos

| ID | Requirement | Risk Addressed | Verification | Status |
|---|---|---|---|---|
| SEC-001 | Credenciais do MinIO devem ser externalizadas e nunca versionadas. | Credential disclosure | Revisão de configuração e secret scan | Draft |
| SEC-002 | O bucket de documentos deve permanecer privado e o acesso deve ocorrer pelo serviço autorizado. | Unauthorized access | Teste de permissões e integração | Draft |
| SEC-003 | Autenticação e autorização devem ser validadas independentemente da UI. | Spoofing/elevation of privilege | Testes de segurança da API | Draft |
| SEC-004 | Nome, tipo, tamanho e conteúdo devem ser tratados como entrada não confiável e validados. | Injection/tampering/DoS | Testes de validação e abuso | Draft |
| SEC-005 | A referência do objeto deve ser gerada pelo servidor e não aceitar path arbitrário do cliente. | Object overwrite/path traversal | Teste de chaves e entradas maliciosas | Draft |
| SEC-006 | Respostas e erros não devem revelar SDK, endpoint, bucket privado ou credenciais. | Information disclosure | Testes de erro e revisão de payloads | Draft |
| SEC-007 | Devem existir limites de tamanho e proteção contra abuso antes do release. | Denial of service | Teste de limite e carga controlada | Draft |
| SEC-008 | Logs, métricas e traces não podem conter bytes do documento ou segredos. | Sensitive data disclosure | Teste de redaction e inspeção de logs | Draft |
| SEC-009 | Dependências do SDK e imagens de infraestrutura devem ser versionadas e verificadas. | Supply-chain compromise | Dependency/security scan | Draft |

## Authentication

A operação deve reutilizar o mecanismo de autenticação aprovado para a API. Esta feature não cria uma nova identidade ou sessão. A ausência de autenticação aplicável impede a liberação para consumidores não confiáveis.

## Authorization

O consumidor só pode registrar documentos dentro do escopo autorizado. O serviço deve aplicar autorização no backend, independentemente de qualquer frontend.

## Session / Token Handling

Tokens não devem ser persistidos no documento, enviados ao MinIO como dado de negócio ou registrados em logs. O token deve ser propagado somente pelos mecanismos aprovados para autenticação da API.

## Input Validation

- Rejeitar nome original e tipo de conteúdo ausentes ou em branco.
- Rejeitar tamanho negativo.
- Detectar divergência entre tamanho declarado e conteúdo recebido.
- Aplicar limite máximo de upload definido antes do release.
- Tratar tipo de conteúdo e nome como valores não confiáveis.
- Não aceitar referência de objeto ou caminho arbitrário do cliente.

## Output Encoding

Mensagens de erro devem usar o contrato provider-neutral da API. Detalhes internos do MinIO, stack traces, endpoints e credenciais não devem ser devolvidos ao consumidor.

## Secrets

Endpoint, bucket, access key e secret key devem ser fornecidos por ambiente ou mecanismo de secrets. Valores de exemplo não podem ser usados como credenciais de produção.

## Sensitive Data

O conteúdo binário deve permanecer no bucket privado. O PostgreSQL guarda metadados e referência. `objectKey`, nome original e tipo de conteúdo devem ser tratados como potencialmente sensíveis conforme o contexto do documento.

## Logging / Auditability

Registrar somente document ID, correlation ID, resultado, categoria do erro, duração e volume necessário para operação. Redigir conteúdo, credenciais, tokens e payloads completos.

## Rate Limiting / Abuse Prevention

O endpoint deve ter limite de tamanho e controles de abuso compatíveis com o ambiente. Valores de limite e taxa devem ser definidos antes do release e monitorados.

## Dependency / Supply Chain

O SDK MinIO e imagens de infraestrutura devem ser fixados em versões aprovadas, submetidos a scanning e atualizados sem introduzir dependências desnecessárias no domínio.

## Security Testing

- Testar acesso não autorizado ao endpoint e ao bucket.
- Testar tentativa de path/object key controlada pelo cliente.
- Testar uploads acima do limite e conteúdo inconsistente.
- Verificar ausência de secrets e conteúdo em logs, traces e respostas.
- Executar dependency scan e revisão de configuração antes do release.
