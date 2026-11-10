# Execução — Fase 7.5: validação manual do fluxo e correções

Ambiente-alvo: primeiro staging local; publicação Render somente quando o mesmo caso
estiver reproduzível e autorizado.
Status: aguardando teste manual do responsável pelo projeto.

## Pré-condições

- [ ] Commits das Fases 5, 6 e 7 disponíveis.
- [ ] URLs e credenciais de teste confirmadas sem registrar segredos.
- [ ] Arquivos de teste identificados e descartáveis.
- [ ] Console/Network do navegador disponível para evidência técnica.

## Procedimento

1. Registrar a versão e o ambiente testado.
2. Executar cadastro, login, logout e login novamente.
3. Executar upload, processamento, notificação, consulta, preview, download e exclusão.
4. Executar arquivo inválido, retry, sessão expirada e acesso anônimo.
5. Para cada falha, guardar status HTTP, endpoint, mensagem funcional e correlação,
   removendo tokens, cookies e conteúdo sensível da evidência.
6. Reproduzir a falha localmente antes de corrigir.
7. Implementar a correção mínima, adicionar regressão e executar novamente o fluxo.
8. Só publicar uma correção depois que o staging local estiver verde.

## Rollback

Se uma correção quebrar o fluxo, parar a publicação, voltar ao último commit aprovado,
reexecutar os smoke tests e preservar a evidência do defeito. Não apagar dados de teste
sem confirmar o alvo.

## Resultado obrigatório

Checklist preenchido, defeitos reproduzíveis corrigidos ou classificados como limitações,
regressões verdes e commit próprio da fase antes da Fase 8.
