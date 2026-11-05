# Execução — Fase 2: ambiente cloud demonstrável sem custo

Ambiente-alvo: Render Free + Neon Free + CloudAMQP Little Lemur + Cloudflare R2.
Status: artefatos locais preparados; execução externa depende das contas e recursos free tier.

## Pré-condições

- [ ] CI/CD e imagem da Fase 1 aprovados.
- [x] Composição free tier e custo-alvo zero confirmados.
- [ ] Contas, URLs e recursos externos criados.
- [ ] Secrets e permissões mínimas configurados.
- [ ] Banco, broker e storage externos disponíveis.
- [ ] Plano de rollback e limite de custo registrados.

## Passos

1. Criar os serviços Render a partir de `render.yaml` sem alterar produção inexistente.
2. Criar o banco Neon, o broker CloudAMQP e os buckets R2 dentro das franquias gratuitas.
3. Configurar secrets por ambiente e validar apenas nomes, nunca imprimir valores.
4. Promover a imagem aprovada pelo digest ou deixar o Render construir a revisão aprovada.
5. Aplicar Flyway e verificar `/actuator/health`.
6. Publicar frontend e configurar sua URL de API e origem CORS.
7. Executar smoke completo de upload até exclusão.
8. Registrar URLs, commit, imagem, dependências e resultado.
9. Executar rollback controlado e confirmar recuperação.

## Rollback

Voltar ao digest anterior e, se necessário, à configuração anterior. Não remover banco,
filas ou objetos para corrigir uma falha de deploy; preservar evidências para diagnóstico.

## Resultado obrigatório

URLs, log de smoke cloud, registro de secrets sem valores, hash da imagem, rollback e
checklist aprovado.

