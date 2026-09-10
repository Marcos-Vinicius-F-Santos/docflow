# Threat Model — Armazenamento binário de documentos

Owner: Engineering  
Review date: 2026-09-08  
Related feature spec: `specs/02-product/document-upload/spec.md`

## 1. Scope

O escopo cobre o recebimento de metadados e conteúdo binário, a persistência do conteúdo no MinIO, a persistência da referência no PostgreSQL e a exposição do resultado pela API.

Não cobre OCR, antivírus, download público, retenção automática ou mensageria RabbitMQ.

## 2. Assets

| Asset | Sensitivity | Impact if Compromised |
|---|---|---|
| Conteúdo binário | Alta | Exposição, adulteração ou perda de documentos |
| Metadados de `Document` | Média/Alta | Exposição de informação e quebra de integridade |
| `objectKey` | Média | Associação indevida, colisão ou acesso indireto |
| Credenciais MinIO | Crítica | Leitura, alteração ou destruição de todo o bucket |
| Estado de armazenamento | Alta | Falso sucesso ou processamento incorreto |
| Logs, métricas e traces | Média/Alta | Vazamento de dados e diagnóstico comprometido |

## 3. Actors

- Cliente legítimo da API.
- Cliente não autenticado ou sem autorização.
- Atacante de rede ou intermediário.
- Processo DocFlow.
- Administrador/operador de infraestrutura.
- MinIO e PostgreSQL como dependências confiáveis dentro de seus boundaries.

## 4. Trust Boundaries

- Cliente ↔ API HTTP.
- API/Service ↔ PostgreSQL.
- API/Service ↔ `DocumentStorage`.
- `MinioDocumentStorage` ↔ MinIO.
- Aplicação ↔ ambiente de configuração/secrets.
- Operação ↔ logs, métricas, traces e dashboards.

## 5. Entry Points

- Operação de registro de documento com conteúdo binário.
- Consulta de documento por ID.
- Endpoint MinIO usado pelo adapter.
- Variáveis de configuração e credenciais fornecidas ao processo.
- Logs e sinais operacionais consumidos por operadores.

## 6. Threats

### THREAT-001 — Object key controlada pelo cliente

Category: Tampering / Elevation of Privilege  
Likelihood: Medium  
Impact: High  
Risk: O cliente força sobrescrita, colisão ou acesso a outro objeto.

Attack path:

O cliente envia um nome ou caminho tratado como identidade do objeto; o serviço grava em uma localização escolhida pelo atacante.

Mitigations:

- Referência gerada pelo servidor.
- Nome original tratado apenas como metadado.
- Regras de unicidade e controle de concorrência.

Residual risk: O formato final da referência ainda deve ser validado no contrato de dados.

Verification:

- Testar separadores de caminho, caracteres especiais e colisões.
- Confirmar que o cliente não escolhe a referência persistida.

### THREAT-002 — Acesso não autorizado ao conteúdo

Category: Spoofing / Information Disclosure  
Likelihood: Medium  
Impact: High  
Risk: Um usuário acessa ou grava documentos fora de seu escopo.

Attack path:

O atacante chama a API sem autenticação/autorização válida ou acessa diretamente o endpoint MinIO.

Mitigations:

- Bucket privado.
- Autenticação e autorização verificadas no backend.
- MinIO não exposto como interface pública de negócio.

Residual risk: O mecanismo de identidade da aplicação ainda não está documentado no repositório.

Verification:

- Testes de acesso anônimo, acesso cruzado e permissões do bucket.

### THREAT-003 — Exaustão de recursos por upload

Category: Denial of Service  
Likelihood: High  
Impact: High  
Risk: Uploads grandes ou numerosos consomem memória, conexões, disco ou capacidade do MinIO.

Attack path:

O atacante envia conteúdo acima do limite ou muitas operações simultâneas.

Mitigations:

- Limite máximo de tamanho.
- Streaming e limites de recursos.
- Rate limiting/abuse prevention.
- Métricas de volume e saturação.

Residual risk: Limites quantitativos ainda precisam ser aprovados.

Verification:

- Testes de limite, concorrência controlada e carga.

### THREAT-004 — Conteúdo adulterado ou tamanho inconsistente

Category: Tampering  
Likelihood: Medium  
Impact: High  
Risk: O sistema associa metadata incorreta ou bytes diferentes ao documento.

Attack path:

O tamanho declarado diverge do stream ou a transferência é interrompida sem detecção.

Mitigations:

- Validar tamanho e tipo.
- Confirmar a escrita.
- Não marcar `COMPLETED` sem referência persistida.

Residual risk: Validação semântica e antivírus estão fora do escopo.

Verification:

- Testes de bytes, tamanho, tipo e transferência interrompida.

### THREAT-005 — Vazamento de credenciais ou conteúdo em observabilidade

Category: Information Disclosure  
Likelihood: Medium  
Impact: High  
Risk: Logs ou respostas expõem secrets, bytes ou detalhes internos.

Attack path:

Exceções do SDK, payloads ou configurações são registrados integralmente.

Mitigations:

- Erros provider-neutral.
- Redaction de secrets e payloads.
- Logs correlacionados sem conteúdo.
- Secret scan e testes de observabilidade.

Residual risk: Integrações futuras podem introduzir novos campos sensíveis.

Verification:

- Inspecionar logs, traces, métricas e payloads em sucesso e falha.

### THREAT-006 — Falha parcial e conteúdo órfão

Category: Tampering / Repudiation  
Likelihood: Medium  
Impact: High  
Risk: MinIO confirma, mas PostgreSQL não persiste a referência, ou o resultado fica inconclusivo.

Attack path:

A conexão cai entre as duas persistências ou a transação final falha.

Mitigations:

- Estados explícitos.
- Referência estável.
- Retry, compensação e reconciliação.
- Alertas e runbook.

Residual risk: PostgreSQL e MinIO não participam de uma transação distribuída.

Verification:

- Testes de falha entre storage e banco, retry e recuperação.

### THREAT-007 — Replay ou concorrência da mesma operação

Category: Tampering / Denial of Service  
Likelihood: Medium  
Impact: Medium  
Risk: Retries criam múltiplas associações ou sobrescrevem um documento concluído.

Attack path:

O cliente repete uma chamada após timeout ou duas requisições processam o mesmo documento simultaneamente.

Mitigations:

- Referência estável por documento.
- Controle de concorrência persistente.
- Não sobrescrever `COMPLETED` nesta feature.

Residual risk: A política final de idempotência deve ser registrada no contrato.

Verification:

- Testes concorrentes e de retry com resultado conhecido e inconclusivo.

## 7. Abuse Cases

- Enviar conteúdo acima do limite repetidamente.
- Forçar nomes com path traversal ou colisões.
- Tentar acessar diretamente o bucket.
- Repetir requisições após timeout para provocar duplicação.
- Enviar conteúdo incompatível com o tamanho declarado.
- Observar mensagens de erro para descobrir endpoint ou credencial.

## 8. Security Assumptions

- O ambiente de execução protege as variáveis de secrets.
- PostgreSQL e MinIO são acessados por canais de rede controlados.
- O bucket não é público.
- A API terá autenticação/autorização apropriada antes de produção.
- A ausência de antivírus nesta feature é um risco conhecido e aceito somente para o escopo definido.

## 9. Open Risks

- Limite de tamanho, rate limit e tipos permitidos ainda não possuem valores aprovados.
- O mecanismo de identidade/autorização não está presente no código analisado.
- O Compose local e o provisionamento do bucket precisam ser validados antes da integração.
- Conteúdo órfão exige procedimento operacional testado.
