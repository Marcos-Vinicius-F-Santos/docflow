# Execução — Fase 6: contratos e operação

Ambiente-alvo: staging e cópia controlada do banco/objetos de teste.  
Status: bloqueado até runbooks, backup e restore serem revisados.

## Pré-condições

- [ ] Fases 0–5 aprovadas ou limitações explicitamente aceitas.
- [ ] Conta de staging e banco de teste disponíveis.
- [ ] Backup não sobrescreve o original.
- [ ] Procedimento de restauração e janela de manutenção aprovados.
- [ ] Nenhum comando destrutivo será usado sem validação do alvo.

## Passos

1. Gerar OpenAPI e validar exemplos contra o ambiente.
2. Verificar readiness/liveness durante dependência disponível e indisponível.
3. Executar backup do PostgreSQL em destino identificável.
4. Restaurar em banco separado e verificar migrations, documentos e fluxo.
5. Exercitar runbooks de incidente, DLQ, storage e rollback.
6. Revisar retenção e limpeza com dados de teste identificados.
7. Atualizar diagramas, ADRs e matriz de evidências.
8. Registrar o commit final da fase.

## Rollback

Conservar o banco original e os artefatos de backup. Se a documentação divergir, reverter
o commit documental, sem apagar evidências nem alterar o banco original para compensar.

## Resultado obrigatório

OpenAPI validada, backup/restore comprovados, runbooks exercitados, diagramas atualizados
e checklist aprovado.

