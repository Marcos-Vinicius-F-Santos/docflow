# Execução — Fase 2: ambiente cloud demonstrável

Ambiente-alvo: staging cloud aprovado para o DocFlow.  
Status: bloqueado até provedor, credenciais e custos serem aprovados.

## Pré-condições

- [ ] CI/CD e imagem da Fase 1 aprovados.
- [ ] Provedor, conta/projeto e região confirmados.
- [ ] Secrets e permissões mínimas configurados.
- [ ] Banco, broker e storage externos disponíveis.
- [ ] Plano de rollback e limite de custo registrados.

## Passos

1. Criar/selecionar recursos de staging sem alterar produção inexistente.
2. Configurar secrets por ambiente e validar apenas nomes, nunca imprimir valores.
3. Promover a imagem aprovada pelo digest.
4. Aplicar Flyway e verificar readiness/liveness disponíveis.
5. Publicar frontend e configurar sua URL de API.
6. Executar smoke completo de upload até exclusão.
7. Registrar URLs, commit, imagem, dependências e resultado.
8. Executar rollback controlado e confirmar recuperação.

## Rollback

Voltar ao digest anterior e, se necessário, à configuração anterior. Não remover banco,
filas ou objetos para corrigir uma falha de deploy; preservar evidências para diagnóstico.

## Resultado obrigatório

URLs, log de smoke cloud, registro de secrets sem valores, hash da imagem, rollback e
checklist aprovado.

