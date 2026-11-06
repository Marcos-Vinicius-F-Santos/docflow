# Backlog do DocFlow

Pendências funcionais e de experiência identificadas durante a validação do ambiente
publicado. Os itens aqui registrados não estão em execução até serem priorizados e
aprovados no plano da fase correspondente.

## BKL-001 — Exibir erros de backend no frontend

- **Status:** Backlog
- **Prioridade:** Alta
- **Origem:** Validação do fluxo Neon Auth e Hub publicado
- **Escopo:** autenticação, chamadas REST protegidas e notificações WebSocket

### Problema

Quando o usuário aciona uma operação e o backend responde com erro, a interface pode
permanecer em estado de carregamento ou exibir apenas a falha no console do navegador.
Isso impede que o usuário saiba se precisa corrigir os dados, iniciar sessão, tentar
novamente ou aguardar uma dependência.

### Resultado esperado

Toda ação iniciada pelo usuário deve terminar em um estado visual explícito de sucesso,
erro ou tentativa novamente. A mensagem deve ser compreensível, não expor detalhes
sensíveis e, quando aplicável, diferenciar autenticação ausente, autorização negada,
validação, indisponibilidade temporária e erro inesperado.

### Critérios de aceite

- Falhas de cadastro e login exibem a mensagem retornada pelo provedor em linguagem
  adequada ao usuário, sem depender do DevTools.
- Respostas `401` e `403` das operações do Hub orientam o usuário a iniciar sessão ou
  informam que ele não tem permissão.
- Falhas de listagem, upload, download, retry e exclusão encerram o carregamento e
  oferecem nova tentativa quando a operação for recuperável.
- Falha ou perda do WebSocket altera o indicador de conexão para um estado explicativo,
  sem produzir tentativas infinitas ou ruído perceptível antes da autenticação.
- O frontend mantém testes para o mapeamento de cada classe de erro e para os estados
  de carregamento, sucesso, erro e retry.
- O backend continua retornando contratos de erro consistentes e não vaza stack trace,
  credenciais, tokens ou dados de outro tenant.

### Dependências e observações

- Revisar o fallback da rota `/auth` no Render Static Site (`/*` para `/index.html`).
- Confirmar o fluxo de recuperação de senha do Neon Auth para usuários criados fora do
  DocFlow.
- Implementar em fase própria de melhoria de UX/robustez; não faz parte do fechamento
  automático da Fase 4.
