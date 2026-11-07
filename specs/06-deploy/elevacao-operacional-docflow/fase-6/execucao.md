# Execução — Fase 6: contratos e operação

Ambiente-alvo: staging e cópia controlada do banco/objetos de teste.  
Status: execução operacional concluída; commit da fase autorizado.

## Pré-condições

- [x] Fases 0–5 aprovadas ou limitações explicitamente aceitas.
- [x] Conta de staging e banco de teste disponíveis.
- [x] Backup não sobrescreve o original.
- [x] Procedimento de restauração e janela de manutenção aprovados.
- [x] Nenhum comando destrutivo será usado sem validação do alvo.

## Passos

1. [x] Gerar OpenAPI e validar o documento YAML e a suíte contra o contrato.
2. [x] Verificar readiness/liveness no backend recém-reiniciado no kind; ambos retornaram HTTP 200.
3. [x] Executar backup PostgreSQL em arquivo temporário identificável, sem sobrescrita.
4. [x] Restaurar em banco separado e verificar migrations; 8 registros de Flyway encontrados.
5. [x] Exercitar rollback para a revisão anterior e restaurar a revisão validada; staging retornou saudável.
6. [x] Revisar retenção e limpeza com dados de teste identificados.
7. [x] Atualizar diagramas, ADRs e matriz de evidências.
8. [x] Registrar o commit final da fase após aprovação de Marcos.

## Rollback

Conservar o banco original e os artefatos de backup. Se a documentação divergir, reverter
o commit documental, sem apagar evidências nem alterar o banco original para compensar.

## Resultado obrigatório

OpenAPI validada, backup/restore comprovados, probes operacionais verificadas, rollback
exercitado, runbooks, diagramas e checklist atualizados. Commit da fase autorizado.

