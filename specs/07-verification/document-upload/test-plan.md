# Verification Plan — Armazenamento binário de documentos

Owner: Engineering  
Related spec: `specs/02-product/document-upload/spec.md`  
Related architecture: `specs/03-architecture/document-upload/architecture.md`  
Related ADR: `specs/01-decisions/ADR/ADR-001-document-binary-storage.md`

## 1. Verification Strategy

A verificação combina testes de domínio, service, API, contrato, migration, integração com PostgreSQL/MinIO, falhas parciais, concorrência, segurança e sinais operacionais.

Nenhum teste deve considerar sucesso quando o conteúdo não estiver confirmado e a referência não estiver persistida.

## 2. Traceability

| Requirement | Test / Evidence | Level | Status |
|---|---|---|---|
| FR-001 | TEST-001 | API/Integration | Planned |
| FR-002 | TEST-002 | Unit/API | Planned |
| FR-003 | TEST-003 | Integration | Planned |
| FR-004 | TEST-004 | Integration/Database | Planned |
| FR-005 | TEST-005 | Integration | Planned |
| FR-006 | TEST-006 | Service/API | Planned |
| FR-007 | TEST-007 | Failure/Integration | Planned |
| FR-008 | TEST-008 | Compatibility/Integration | Planned |
| FR-009 | TEST-009 | Concurrency/Integration | Planned |
| NFR-001 | TEST-010 | Integration | Planned |
| NFR-002 | TEST-011 | Database/Service | Planned |
| NFR-003 | TEST-012 | Architecture/Unit | Planned |
| NFR-004 | TEST-SEC-001 | Security | Planned |
| NFR-005 | TEST-SEC-002 | Security/Observability | Planned |
| NFR-006 | TEST-OPS-001 | Operational | Planned |
| NFR-007 | TEST-013 | Failure/Recovery | Planned |
| NFR-008 | TEST-014 | Regression/Compatibility | Planned |
| SEC-001–SEC-009 | TEST-SEC-001–TEST-SEC-009 | Security | Planned |

## 3. Unit Tests

- Validar invariantes de nome, tipo e tamanho.
- Validar estado inicial `PENDING`.
- Validar transições permitidas e rejeitar `COMPLETED` sem referência.
- Validar geração/reuso de referência estável no service.
- Mockar `DocumentStorage` para sucesso, falha conhecida e resultado inconclusivo.
- Confirmar que o repository não é chamado para entradas inválidas.
- Confirmar que tipos do SDK MinIO não aparecem no domínio ou service.

## 4. Integration Tests

- PostgreSQL real com Flyway aplicada.
- MinIO real em container.
- Registro de conteúdo e persistência da referência.
- Comparação entre bytes enviados e bytes armazenados.
- Preservação de tamanho e tipo.
- Falha de storage e finalização do documento.
- Leitura de documentos existentes sem `objectKey`.

## 5. Contract Tests

- Validar `specs/04-contracts/document-upload/openapi.yaml`.
- Validar request multipart conforme o contrato.
- Validar responses `201`, `202`, `400`, `409`, `500`, `503` e `404`.
- Validar enum de status.
- Validar payload provider-neutral de erros.

## 6. End-to-End Tests

- Cliente envia metadados e conteúdo.
- DocFlow persiste o objeto no MinIO e a referência no PostgreSQL.
- Cliente consulta o documento e observa o estado correto.
- Indisponibilidade do MinIO não resulta em sucesso.

## 7. Security Tests

- Credenciais não aparecem no repositório, respostas ou logs.
- Bucket não permite acesso público indevido.
- Cliente não controla arbitrariamente a referência do objeto.
- Path traversal, caracteres especiais e colisões são rejeitados ou neutralizados.
- Upload acima do limite é rejeitado.
- Autenticação e autorização são verificadas no backend.
- Dependências e imagens passam por scanning.

## 8. Performance Tests

- Medir latência de storage por tamanho de conteúdo.
- Medir throughput e saturação sob concorrência controlada.
- Verificar uso de memória durante uploads.
- Definir metas quantitativas antes do release; nenhum valor é inventado nesta etapa.

## 9. Migration Tests

- Executar migration em banco vazio.
- Executar migration em banco com documentos legados.
- Confirmar `object_key` nullable para legado.
- Confirmar valor inicial de `version`.
- Confirmar unicidade de referência.
- Confirmar bloqueio de `COMPLETED` sem referência.
- Validar rollback/forward recovery documentado.

## 10. Failure / Chaos Tests

- MinIO indisponível antes da escrita.
- Timeout durante a escrita.
- Conexão interrompida após possível sucesso do MinIO.
- Falha ao persistir referência/estado no PostgreSQL.
- Falha de confirmação da operação.
- Retry com resultado conhecido e inconclusivo.
- Reconciliação de objeto órfão.

## 11. Compatibility Tests

- Endpoints de consulta existentes continuam funcionando.
- Documentos antigos sem referência continuam consultáveis.
- Invariantes atuais continuam sendo aplicadas.
- Mudança do registro metadata-only para conteúdo segue o OpenAPI aprovado.
- Nenhum package ou boundary público definido pelo ADR é quebrado.

## 12. Manual Verification

- Validar `docker compose config`.
- Iniciar PostgreSQL e MinIO com credenciais externalizadas.
- Confirmar existência e privacidade do bucket.
- Exercitar upload válido e consultar estado.
- Desligar MinIO e confirmar falha diagnóstica.
- Inspecionar logs, métricas, traces, dashboards e alertas.
- Executar procedimento de reconciliação conforme runbook.

## 13. Exit Criteria

- [ ] Todos os requisitos `Must` verificados.
- [ ] Critérios `AC-001`–`AC-008` verificados.
- [ ] Nenhum sucesso sem conteúdo confirmado e referência persistida.
- [ ] Nenhum defeito crítico aberto.
- [ ] Regression suite verde.
- [ ] Migration e compatibilidade validadas.
- [ ] MinIO real coberto por integração.
- [ ] Security checks concluídos.
- [ ] Observabilidade, alertas e runbook validados.
- [ ] Riscos de release específicos concluídos.
