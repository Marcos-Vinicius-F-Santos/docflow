# Execução — Fase 4: segurança e multi-tenant

Ambiente-alvo: staging isolado, nunca produção sem aprovação explícita.  
Status: implementação local concluída e validada; promoção para staging aguarda secrets de Neon Auth e teste E2E com dois usuários.

## Pré-condições

- [x] Fase 3 aprovada ou observabilidade equivalente disponível.
- [ ] Provedor de identidade configurado no ambiente de execução com `NEON_AUTH_JWKS_URL` válido.
- [x] Secrets de assinatura/validação não estão no repositório; Render usa variáveis secretas.
- [ ] Backup do banco e rollback da migration verificados no ambiente remoto.
- [ ] Dois usuários/tenants de teste disponíveis no staging.

## Passos

1. [x] Aplicar migration em banco de teste e verificar dados existentes.
2. [ ] Promover a imagem aprovada com configuração de identidade e CORS no staging.
3. [x] Validar proteção de endpoints e endpoints operacionais públicos no teste MVC.
4. [ ] Executar cenário remoto de dois usuários com mesmo nome de arquivo.
5. [x] Validar o caminho de código para upload, listagem, detalhe, download, retry, exclusão, logs e WebSocket.
6. [ ] Provocar tentativa remota de acesso indevido e verificar resposta, auditoria e ausência de vazamento.
7. [x] Implementar rate limit; smoke OWASP permanece reservado para a Fase 9.
8. [x] Registrar evidências e congelar o commit da fase após os testes locais.

## Rollback

Usar backup e procedimento de rollback controlado da migration, além do digest anterior da imagem.
Não remover dados para contornar falha de autorização ou tenant.

## Resultado obrigatório

Matriz de permissões, testes locais de isolamento, migration/rollback documentados e
checklist aprovado. A validação E2E remota e o relatório OWASP permanecem pendentes para
as fases/ambientes explicitamente previstos.

