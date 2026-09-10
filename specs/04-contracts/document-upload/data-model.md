# Data Model — Armazenamento binário de documentos

## Entities

### Document

O documento continua sendo a entidade de metadados e estado. O conteúdo binário permanece fora do PostgreSQL.

| Field | Type | Nullable | Constraints | Description |
|---|---|---:|---|---|
| id | UUID | No | PK | Identidade estável do documento |
| status | DocumentStatus | No | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | Estado do registro e do armazenamento |
| originalFilename | String | No | Não vazio | Metadado do nome original |
| contentType | String | No | Não vazio | Tipo declarado do conteúdo |
| sizeBytes | BIGINT | No | `>= 0` | Tamanho declarado do conteúdo |
| objectKey | String | Yes | Único quando presente | Referência provider-neutral ao objeto armazenado |
| version | BIGINT | No | Inicial `0` | Controle de concorrência otimista |
| createdAt | Instant | No | Imutável | Data de criação |
| updatedAt | Instant | No | Atualizado em mudanças | Data da última alteração |

## Relationships

`Document.objectKey` identifica um único objeto no MinIO quando presente. Os bytes não são uma relação JPA nem uma coluna binária.

## Invariants

- `originalFilename` não pode ser nulo ou vazio.
- `contentType` não pode ser nulo ou vazio.
- `sizeBytes` deve ser maior ou igual a zero.
- `objectKey` não pode ser fornecido arbitrariamente pelo cliente.
- Uma referência presente deve ser única.
- `status = COMPLETED` exige `objectKey` presente.
- `status != COMPLETED` não representa armazenamento concluído.
- O mesmo documento não pode possuir duas associações de conteúdo válidas.

## Indexes

- Chave primária em `id`.
- Índice/constraint de unicidade em `objectKey` quando não nulo.
- A estratégia de acesso para documentos em `PROCESSING` deve permitir identificar casos pendentes de reconciliação.

## Data Lifecycle

```text
PENDING
  → PROCESSING
  → COMPLETED  (storage confirmado + referência persistida)
  → FAILED     (falha conhecida)
```

Resultado inconclusivo permanece em estado não-successo até retry ou reconciliação. Documentos legados podem possuir `objectKey = null` e continuam consultáveis.

## Retention

Esta feature não define retenção, exclusão automática, versionamento ou deduplicação. O ciclo de vida futuro do objeto deve preservar a associação enquanto o documento estiver ativo.

## Privacy Classification

- Conteúdo binário: potencialmente sensível; armazenado somente no bucket privado.
- Metadados: potencialmente sensíveis; acesso e retenção dependem do contexto do documento.
- Credenciais: segredo operacional; nunca persistido na entidade nem versionado.
- `objectKey`: referência interna; não deve expor detalhes de acesso ao provider sem contrato aprovado.

## Migration Strategy

### Current State

`documents` possui metadados, estado e timestamps, mas não possui referência ao conteúdo nem controle persistente de concorrência.

### Target State

`documents` possui `object_key` nullable, `version` com valor inicial compatível e constraints que garantem unicidade da referência e impossibilitam `COMPLETED` sem referência.

### Migration Steps

1. Adicionar `object_key` como nullable para preservar registros existentes.
2. Adicionar `version` com valor inicial `0` para todas as linhas existentes.
3. Criar unicidade para referências não nulas.
4. Adicionar a regra de consistência entre `status` e `object_key`.
5. Validar schema e leitura de documentos legados antes de ativar o novo fluxo.

## Backward Compatibility

- Migrations existentes não serão alteradas.
- Registros criados antes da feature permanecem válidos sem conteúdo.
- Consultas existentes continuam retornando metadados e estado.
- Nenhum conteúdo fictício será criado para preencher referências ausentes.
- A alteração do contrato de registro será tratada como mudança explícita e versionada/migrada conforme o OpenAPI.

## Rollback / Forward Recovery

- Rollback da aplicação não remove automaticamente objetos já gravados.
- Se a gravação no MinIO ocorrer antes da finalização no banco, o documento permanece não-successo e entra no fluxo de retry/reconciliação.
- Conteúdo órfão deve ser identificado por referência estável e tratado por compensação ou procedimento operacional aprovado.
- A migration deve ser expand-only durante o rollout; a remoção de colunas ou constraints fica fora desta feature.
- A reversão de código deve preservar a leitura de linhas com `object_key` e `version` até a conclusão da janela de compatibilidade.
