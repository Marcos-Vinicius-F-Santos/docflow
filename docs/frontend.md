# Frontend Angular do DocFlow

O frontend Angular implementa o fluxo de documentos: seleção local, upload multipart,
acompanhamento por identificador, listagem, download de documentos `COMPLETED` e
exclusão definitiva com confirmação explícita.

Autenticação, autorização, multi-tenant, preview e download em lote permanecem fora do
escopo desta versão.

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
`/documents/new`; a listagem fica disponível em `/documents` e, após um upload aceito,
a aplicação navega para `/documents/{documentId}`.

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

1. Abra a tela de novo documento e envie um arquivo não vazio, com nome e tipo
   informados, de até 50 MiB.
2. Acesse `/documents` e confirme que os documentos são exibidos com os sete campos e
   todos os status retornados pelo backend.
3. Para um documento `COMPLETED`, acione `Baixar` e confirme que o navegador recebe o
   arquivo com o nome original.
4. Acione `Excluir`, confirme a ação e confirme que o item desaparece da listagem.
5. Cancele uma confirmação de exclusão e confirme que nenhuma requisição é enviada.
6. Verifique a lista vazia e as mensagens aprovadas para falha de listagem, download e
   exclusão.
7. Abra novamente `/documents/new` e `/documents/{documentId}` para confirmar que
   upload e consulta por identificador continuam funcionando.

Para uma execução reproduzível sem navegador, o contrato HTTP também pode ser
validado com:

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@.\exemplo.pdf;type=application/pdf"
curl.exe http://localhost:8080/documents/{documentId}
curl.exe http://localhost:8080/documents
curl.exe -OJ http://localhost:8080/documents/{documentId}/content
curl.exe -i -X DELETE http://localhost:8080/documents/{documentId}
```

O download HTTP só está disponível para documentos `COMPLETED`. A exclusão é
definitiva e o endpoint não aceita `objectKey` nem referências de storage do cliente;
os detalhes da política estão no [ADR-006](../specs/02-arquitetura/DECISAO/ADR-006-exclusao-definitiva-documentos.md).

## Rollback

O frontend é independente do schema, filas e buckets do backend. Para reverter esta
entrega, interrompa o servidor frontend e remova ou reverta apenas o artefato do
frontend correspondente ao commit aprovado. Não remova volumes, mensagens, buckets
ou dados do backend como parte do rollback.
