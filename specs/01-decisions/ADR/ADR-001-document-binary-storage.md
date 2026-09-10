# ADR-001 — Armazenamento binário fora do PostgreSQL

Status: Accepted  
Date: 2026-09-08  
Decision owners: Engineering  
Related RFC:  
Supersedes:  
Superseded by:  

## Context

O DocFlow atualmente persiste somente metadados de `Document` no PostgreSQL. A feature de armazenamento binário precisa persistir o conteúdo real, manter a associação com o documento e continuar permitindo a substituição futura do provider de object storage.

PostgreSQL e MinIO são recursos externos distintos. Uma transação comum não pode garantir atomicidade entre a persistência relacional e a escrita dos bytes. Falhas de rede também podem deixar o resultado da escrita inconclusivo.

O projeto já definiu MinIO como provider e `DocumentStorage` como abstração provider-neutral. RabbitMQ não faz parte desta feature.

## Decision Drivers

- Preservar a separação entre domínio e infraestrutura.
- Evitar armazenar conteúdo binário pesado no PostgreSQL.
- Permitir substituição futura do MinIO sem alterar o caso de uso.
- Não declarar sucesso sem conteúdo confirmado e referência persistida.
- Tornar falhas parciais diagnosticáveis e recuperáveis.
- Preservar compatibilidade com documentos existentes.
- Manter a solução simples e compatível com o escopo atual.

## Considered Options

1. Armazenar o binário diretamente no PostgreSQL.
2. Usar o SDK do MinIO diretamente no `DocumentService`.
3. Usar uma porta `DocumentStorage`, adapter MinIO e consistência explícita entre banco e storage.
4. Introduzir processamento assíncrono com RabbitMQ nesta feature.

## Decision

Adotaremos a opção 3:

1. O MinIO armazenará os bytes do documento.
2. O PostgreSQL armazenará metadados, estado e referência estável do objeto.
3. O `DocumentService` dependerá somente de `DocumentStorage`.
4. `MinioDocumentStorage` encapsulará SDK, configuração, bucket e erros do MinIO.
5. A aplicação gerará uma referência estável por documento; o cliente não controlará arbitrariamente essa referência.
6. O fluxo usará estados explícitos: `PENDING` → `PROCESSING` → `COMPLETED`, ou `FAILED` quando a falha for conhecida.
7. `COMPLETED` só será persistido após confirmação do conteúdo no MinIO e persistência da referência no banco.
8. Não haverá transação distribuída. Falhas parciais serão tratadas por retries, compensação e reconciliação, sempre mantendo o documento em estado não-successo enquanto o resultado for inconclusivo.
9. RabbitMQ permanecerá fora do escopo desta decisão e desta feature.

## Rationale

Essa opção preserva os boundaries definidos pela Constitution e pela Feature Spec. O domínio conhece apenas a capacidade de armazenar conteúdo, enquanto o adapter conhece o provider. A separação também permite testar o caso de uso sem depender do SDK e testar o adapter contra MinIO real.

A máquina de estados torna explícita a ausência de atomicidade entre sistemas. Em vez de ocultar uma possível divergência, o sistema mantém estados não-successo e fornece um caminho de recuperação operacional.

## Consequences

### Positive

- Conteúdo e metadados ficam armazenados em componentes adequados a seus papéis.
- O provider pode ser substituído sem alterar regras de negócio.
- O sistema não confirma sucesso antes da persistência necessária.
- Falhas parciais ficam visíveis e podem ser reconciliadas.
- Documentos legados podem continuar existindo sem conteúdo artificial.

### Negative

- A operação envolve duas persistências sem atomicidade compartilhada.
- O fluxo precisa de estados intermediários, retries e reconciliação.
- A API passa a transportar conteúdo binário, aumentando requisitos de limite e segurança.
- Testes de integração exigem um MinIO real ou equivalente controlado.

### Risks

- Conteúdo órfão se a finalização no banco falhar após a escrita no MinIO.
- Estado preso em `PROCESSING` se a reconciliação não for operável.
- Sobrescrita ou duplicação se retries não reutilizarem a referência estável.
- Vazamento de dados se logs registrarem conteúdo, credenciais ou referências indevidas.

## Follow-up Actions

- [ ] Definir o contrato HTTP de conteúdo e metadados.
- [ ] Definir o formato exato da referência e sua migration.
- [ ] Definir política de retry, compensação e reconciliação.
- [ ] Implementar e validar o adapter MinIO.
- [ ] Definir limites de tamanho e tipos de conteúdo.
- [ ] Criar observabilidade e runbook para falhas parciais.
- [ ] Validar autenticação e autorização antes do release.

## Revisit When

Revisitar esta decisão quando:

- processamento assíncrono se tornar requisito do fluxo;
- o volume exigir estratégia de distribuição ou tiering diferente;
- houver necessidade de múltiplos providers simultâneos;
- a política de retenção exigir lifecycle management complexo;
- existir infraestrutura aprovada para transações ou workflows duráveis entre os componentes.
