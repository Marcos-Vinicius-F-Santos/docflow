# Frontend Angular do DocFlow

O frontend Angular implementa o Hub de documentos: seleção local, upload multipart,
listagem central, acompanhamento por identificador, download de documentos `COMPLETED` e
exclusão definitiva com confirmação explícita.

Os documentos da lista central aparecem em blocos compactos. O nome é truncado quando
necessário e o atributo de tooltip mantém o nome completo disponível no hover. Ao clicar
em um bloco, o Hub navega para `/documents/{documentId}`, onde a página de acompanhamento
exibe metadados, status, download, retry elegível, exclusão e preview somente leitura de
PDFs concluídos.

Autenticação, autorização, multi-tenant, preview de formatos que não sejam PDF e download
em lote permanecem fora do escopo desta versão.

## Pré-requisitos

- Node.js `v24.19.0`
- npm `12.0.2`
- backend do DocFlow disponível em `http://localhost:8080`
- PostgreSQL, RabbitMQ e MinIO locais iniciados conforme o procedimento do projeto

O frontend não acessa diretamente banco, filas ou object storage. Em desenvolvimento,
o proxy Angular encaminha `/api/documents` para o backend em `/documents` e `/api/logs`
para `/logs`.

## Executar localmente

Em um terminal, inicie a infraestrutura e o backend seguindo o [procedimento
operacional](document-registration-operations.md). Em outro terminal:

```powershell
Set-Location frontend
npm install
npm start
```

Abra `http://localhost:4200/`. O Hub concentra o upload e a listagem central. Cada
documento listado pode abrir o acompanhamento em `/documents/{documentId}`; não há
rotas separadas `/documents` ou `/documents/new` na navegação.

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

1. Abra o Hub e envie um arquivo não vazio, com nome e tipo informados, de até 50 MiB.
2. Confirme que o Hub exibe os documentos em blocos compactos, com tooltip do nome
   completo, além de até cinco itens em “Atividade” e “Atenção”.
3. Selecione um documento e confirme que `/documents/{documentId}` abre o acompanhamento
   com os sete campos/status retornados pelo backend.
4. Para um documento `COMPLETED` em PDF, confirme que a página oferece `Baixar`, `Excluir`
   e um preview ao final; acione `Baixar` e confirme que o navegador recebe o arquivo com
   o nome original.
5. Acione `Excluir`, confirme a ação e confirme que o item desaparece da listagem.
6. Cancele uma confirmação de exclusão e confirme que nenhuma requisição é enviada.
7. Verifique a lista vazia e as mensagens aprovadas para falha de listagem, download e
   exclusão.

Para uma execução reproduzível sem navegador, o contrato HTTP também pode ser
validado com:

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@.\exemplo.pdf;type=application/pdf"
curl.exe http://localhost:8080/documents/{documentId}
curl.exe http://localhost:8080/documents
curl.exe -OJ http://localhost:8080/documents/{documentId}/content
curl.exe http://localhost:8080/documents/{documentId}/preview
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
