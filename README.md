# DocFlow

Backend REST para registro e acompanhamento de documentos, desenvolvido como projeto
de portfólio de Software Engineering.

O projeto demonstra uma arquitetura organizada por domínio, persistência de metadados
em PostgreSQL, migrações com Flyway, máquina de estados explícita e fronteiras para
armazenamento binário assíncrono.

> **Status:** backend operacional e frontend Angular validados localmente. O fluxo local
> com PostgreSQL, MinIO e RabbitMQ é reproduzível pelo Docker Compose; deploy externo
> continua fora do escopo.

## Visão geral

O DocFlow permite:

- registrar documentos pela API REST;
- persistir metadados e estado do documento em PostgreSQL;
- consultar um documento por identificador;
- validar conteúdo binário recebido como `multipart/form-data`;
- preparar o conteúdo para staging em object storage;
- controlar o ciclo de vida com os estados `PENDING`, `PROCESSING`, `COMPLETED` e
  `FAILED`;
- reconciliar resultados desconhecidos do storage sem promover silenciosamente um
  documento para sucesso.

O frontend Angular permite enviar um documento e acompanhar seu processamento por
identificador. A execução local está descrita em [`docs/frontend.md`](docs/frontend.md).

## Stack

- Java 21
- Spring Boot 4.1
- Spring MVC, Spring Data JPA, Validation e Actuator
- PostgreSQL
- Flyway
- MinIO para object storage local e staging
- RabbitMQ como transporte do fluxo assíncrono, com retries, DLQ e publisher confirms
- Maven Wrapper
- JUnit e Testcontainers

## Pré-requisitos

- Java 21
- Docker Desktop com Docker Compose
- Maven não é necessário: o projeto inclui o Maven Wrapper
- `mc` (opcional), caso seja necessário criar ou inspecionar o bucket do MinIO pela
  linha de comando

## Configuração local

O procedimento abaixo parte de um checkout limpo e não exige preparação manual fora
dos documentos oficiais:

```powershell
git status --short
```

Se houver alterações locais não relacionadas à execução, preserve-as em outro diretório
antes de seguir. A partir daí:

1. Crie o arquivo de ambiente a partir do exemplo:

   ```powershell
   Copy-Item infra/.env.example infra/.env
   ```

2. Revise `infra/.env` e preencha os valores de ambiente, principalmente a senha do
   PostgreSQL. Não versione esse arquivo nem credenciais reais.

3. Inicie PostgreSQL, RabbitMQ e MinIO:

   ```powershell
   Set-Location infra
   docker compose up -d
   ```

   Os serviços locais ficam disponíveis em:

   - PostgreSQL: `localhost:5432`
   - RabbitMQ AMQP: `localhost:5672`
   - RabbitMQ Management: `http://localhost:15672`
   - API do MinIO: `http://localhost:9000`
   - Console do MinIO: `http://localhost:9001`

4. Execute o bootstrap, caso o serviço one-shot ainda não tenha sido executado:

   ```powershell
   docker compose run --rm bootstrap
   ```

   O bootstrap valida as credenciais, cria os buckets de staging e final e provisiona
   o usuário da aplicação MinIO. A execução é idempotente. O procedimento completo está em
   [`docs/document-registration-operations.md`](docs/document-registration-operations.md).

5. Se o Compose já tiver sido usado anteriormente, diagnostique os volumes antes de
   alterar credenciais ou removê-los:

   ```powershell
   docker volume ls --filter name=docflow
   docker compose ps
   docker compose logs --tail=100 postgres rabbitmq minio bootstrap
   ```

   Não use `docker compose down -v` como correção de credencial ou falha de bootstrap.
   Os volumes PostgreSQL, RabbitMQ e MinIO contêm dados que devem ser preservados.

## Executando o backend

Com os serviços locais em execução, o bootstrap concluído e as variáveis de `infra/.env`
disponíveis para o processo, inicie o backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

O backend usa as variáveis `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` para
conectar ao PostgreSQL. As migrações Flyway são aplicadas na inicialização.

O backend valida as credenciais da aplicação MinIO na inicialização, usa buckets separados
para staging e storage final e inicia o scheduler interno de reconciliação quando
`DOCFLOW_RECONCILIATION_ENABLED=true`.

## API

### Registrar documento

O contrato atual exige `multipart/form-data` com uma parte obrigatória chamada `file`:

```bash
curl -X POST http://localhost:8080/documents \
  -F "file=@./exemplo.pdf;type=application/pdf"
```

Limite máximo do conteúdo: `52.428.800` bytes (50 MiB).

### Consultar documento

```bash
curl http://localhost:8080/documents/{documentId}
```

O contrato detalhado da API está em [`docs/api.md`](docs/api.md).

## Testes

Para executar a suíte do backend:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Os testes de integração usam Testcontainers e, portanto, precisam de um daemon Docker
disponível.

Para compilar e empacotar:

```powershell
.\mvnw.cmd clean package
```

Para executar e validar o frontend Angular:

```powershell
Set-Location frontend
npm install
npm start
```

Os testes e o build do frontend estão documentados em
[`docs/frontend.md`](docs/frontend.md).

O smoke test oficial está descrito em [`docs/document-registration-operations.md`](docs/document-registration-operations.md):
ele cobre bootstrap, `POST /documents`, publicação/consumo RabbitMQ, objeto final,
remoção condicional do staging e consulta do estado final. Uma credencial inválida deve
fazer o bootstrap falhar explicitamente; corrija o arquivo local e execute-o novamente,
sem apagar volumes para mascarar o diagnóstico.

## Estrutura do projeto

```text
docflow/
├── backend/      # API Java/Spring Boot, domínio, persistência e testes
├── frontend/     # Aplicação Angular do fluxo de documentos
├── infra/        # Docker Compose e exemplos de configuração local
├── docs/         # Contratos, arquitetura e procedimentos operacionais
└── specs/        # Produto, arquitetura, features, planos e verificação
```

A organização do backend segue o domínio `document`, com separação entre aplicação,
portas, adaptadores, persistência e infraestrutura. A arquitetura detalhada está em
[`specs/02-arquitetura/ARQUITETURA.md`](specs/02-arquitetura/ARQUITETURA.md).

## Documentação

- [Spec do produto](specs/01-produto/SPEC_PRODUTO.md)
- [Arquitetura](specs/02-arquitetura/ARQUITETURA.md)
- [Contrato HTTP](docs/api.md)
- [Banco de dados e migrations](docs/database.md)
- [Operação do registro binário](docs/document-registration-operations.md)
- [Revisão da entrega](docs/document-registration-delivery-review.md)
- [Frontend Angular](docs/frontend.md)
- [Princípios de engenharia](specs/00-principios/principios.md)

## Escopo atual e próximos passos

Ainda estão fora do escopo concluído:

- limpeza automática de objetos órfãos após `FAILED`;
- deploy ou hospedagem externa;
- autenticação, autorização e multi-tenant.

Toda nova funcionalidade deve seguir o fluxo do projeto: spec, arquitetura, plano,
implementação, testes e revisão final antes de qualquer deploy.

## Licença

Nenhuma licença foi definida no repositório até o momento.
