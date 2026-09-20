# Frontend Angular do DocFlow

O frontend Angular implementa o fluxo de registro e acompanhamento de um documento:
seleção local, upload multipart, acompanhamento por identificador e atualização até
`COMPLETED` ou `FAILED`.

Listagem global, download/preview, autenticação, autorização e multi-tenant não fazem
parte desta versão porque não existem contratos correspondentes no backend.

## Pré-requisitos

- Node.js `v24.19.0`
- npm `12.0.2`
- backend do DocFlow disponível em `http://localhost:8080`
- PostgreSQL, RabbitMQ e MinIO locais iniciados conforme o procedimento do projeto

O frontend não acessa diretamente banco, filas ou object storage. Em desenvolvimento,
o proxy Angular encaminha `/api/documents` para o backend em `/documents`.

## Executar localmente

Em um terminal, inicie a infraestrutura e o backend seguindo o [procedimento
operacional](document-registration-operations.md). Em outro terminal:

```powershell
Set-Location frontend
npm install
npm start
```

Abra `http://localhost:4200/`. A rota inicial redireciona para
`/documents/new`; após um upload aceito, a aplicação navega para
`/documents/{documentId}`.

## Comandos de validação

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build
npx prettier --check src/app/app.config.ts src/app/document src/environments/environment.development.ts src/index.html src/styles.scss proxy.conf.json
```

O build de produção usa `src/environments/environment.ts`, cuja base é
`/documents`. Em desenvolvimento, `src/environments/environment.development.ts` usa
`/api/documents` para o proxy local. Nenhum desses arquivos contém credenciais ou
endpoints de MinIO, RabbitMQ ou PostgreSQL.

## Smoke test local

1. Abra a tela de novo documento.
2. Selecione um arquivo não vazio, com nome e tipo informados, de até 50 MiB.
3. Confirme o envio e verifique o progresso e a navegação para a tela do documento.
4. Observe `PENDING`, `PROCESSING` e o estado terminal retornado pelo backend.
5. Teste um arquivo incompatível e um identificador inexistente para confirmar os
   caminhos de erro.

Para uma execução reproduzível sem navegador, o contrato HTTP também pode ser
validado com:

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@.\exemplo.pdf;type=application/pdf"
curl.exe http://localhost:8080/documents/{documentId}
```

## Rollback

O frontend é independente do schema, filas e buckets do backend. Para reverter esta
entrega, interrompa o servidor frontend e remova ou reverta apenas o artefato do
frontend correspondente ao commit aprovado. Não remova volumes, mensagens, buckets
ou dados do backend como parte do rollback.
