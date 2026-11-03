# Checklist de Convergência — Fase 1: CI/CD próprio do DocFlow

Status: não executado  
Spec: `specs/03-features/elevacao-operacional-docflow/spec.md`  
Plano: `specs/04-plano/elevacao-operacional-docflow/fase-1/tarefas.md`

## 1. Pipeline

- [ ] Pull request dispara CI.
- [ ] Push na branch principal dispara CI.
- [ ] Maven/Testcontainers passam.
- [ ] npm/lint/testes/build passam.
- [ ] Imagem é construída e escaneada.
- [ ] Bundle é verificado contra segredos.
- [ ] Badge aponta para o workflow correto.

## 2. Deploy e smoke

| Critério | Resultado | Evidência |
|---|---|---|
| imagem versionada pelo commit | [ ] | URL/tag |
| Flyway | [ ] | log |
| `/actuator/health` | [ ] | resposta |
| upload real | [ ] | documentId/log |
| processamento RabbitMQ | [ ] | log/estado |
| storage final | [ ] | verificação |
| consulta/download/exclusão | [ ] | smoke log |
| rollback | [ ] | log da reversão |

## 3. Rastreabilidade e gate

- [ ] FR-002 → F1-T001 a F1-T003.
- [ ] FR-003 → F1-T004 a F1-T008.
- [ ] AC-003 e AC-004 confirmados.
- [ ] Falhas e limitações registradas.
- [ ] Commit e workflow foram revisados por Marcos.

