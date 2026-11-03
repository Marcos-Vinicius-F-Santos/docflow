# Execução — Fase 4: segurança e multi-tenant

Ambiente-alvo: staging isolado, nunca produção sem aprovação explícita.  
Status: bloqueado até identidade, tenant, permissões e migration serem aprovados.

## Pré-condições

- [ ] Fase 3 aprovada ou observabilidade equivalente disponível.
- [ ] Provedor de identidade configurado.
- [ ] Secrets de assinatura/validação não estão no repositório.
- [ ] Backup do banco e rollback da migration verificados.
- [ ] Dois usuários/tenants de teste disponíveis.

## Passos

1. Aplicar migration em banco de staging e verificar dados existentes.
2. Promover a imagem aprovada com configuração de identidade e CORS.
3. Validar login/token e endpoints anônimos bloqueados.
4. Executar cenário de dois usuários com mesmo nome de arquivo.
5. Testar upload, listagem, detalhe, download, retry, exclusão, logs e WebSocket por perfil.
6. Provocar tentativa de acesso indevido e verificar resposta, auditoria e ausência de vazamento.
7. Executar rate limit e smoke OWASP aprovado.
8. Registrar evidências e congelar o commit da fase.

## Rollback

Usar backup e procedimento de rollback da migration, além do digest anterior da imagem.
Não remover dados para contornar falha de autorização ou tenant.

## Resultado obrigatório

Matriz de permissões, testes de isolamento, migration/rollback, relatório de segurança e
checklist aprovado.

