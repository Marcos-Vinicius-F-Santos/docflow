# Registro de Decisão — Backend S3 compatível no CI

Data: 2026-11-06

Status: Aceita e executada
Feature: `elevacao-operacional-docflow` / Fase 3

## Contexto

O CI dependia da imagem pública `quay.io/minio/minio:RELEASE.2025-07-23T15-54-02Z`
para os testes de integração com Testcontainers. O registry passou a responder
`401 UNAUTHORIZED` para o manifesto e o job ficou em retry por mais de vinte minutos.
O Docker Hub também não oferece mais o repositório comunitário original de forma
confiável para uma execução limpa.

## Decisão

Os testes automatizados obrigatórios usarão o `adobe/s3mock:5.2.0`, um backend S3
compatível, público e próprio para testes, executado pelo Testcontainers. Os buckets
`docflow-staging` e `docflow-documents` serão criados no startup do container.

O adapter de produção continuará usando o SDK MinIO e a configuração de runtime não
será alterada. Assim, a troca é limitada ao ambiente de teste e não muda o contrato
do storage da aplicação.

O teste específico de rejeição de credenciais MinIO deixa de fazer parte do conjunto
obrigatório, pois o S3Mock aceita credenciais fictícias para testes de API. A validação
de credenciais contra um servidor MinIO real fica como exercício manual/opcional.

## Alternativas consideradas

| Alternativa | Decisão |
|---|---|
| Continuar com uma tag antiga no Quay | Rejeitada: o manifesto não é acessível de forma anônima no CI. |
| Voltar para `docker.io/minio/minio` | Rejeitada: o repositório não está disponível de forma confiável. |
| Construir MinIO a partir do código-fonte em todo CI | Adiada: aumenta muito o tempo e a manutenção do pipeline. |
| S3Mock no conjunto obrigatório | Aceita: cobre o contrato S3 usado pelo adapter sem depender da distribuição do MinIO. |

## Consequências

O CI deixa de bloquear por disponibilidade do registry do MinIO e mantém cobertura de
staging, leitura, upload, download, remoção, processamento assíncrono e concorrência
via API S3 compatível. S3Mock é somente uma ferramenta de teste e não representa uma
mudança de storage para staging ou produção.
