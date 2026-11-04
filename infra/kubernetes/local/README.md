# DocFlow em Kubernetes local

Este diretório descreve um ambiente local de staging para o DocFlow usando `kind`.
Ele executa PostgreSQL, RabbitMQ, MinIO, o bootstrap de buckets/credenciais e o
backend em um namespace dedicado.

## Pré-requisitos

- Docker Desktop com containers Linux;
- `kubectl`;
- `kind`;
- `infra/.env` preenchido a partir de `infra/.env.example`.

Os valores de `infra/.env` não são incluídos nos manifests nem no Git. O script de
deploy cria o Secret Kubernetes localmente a partir das chaves necessárias.

## Execução

Na raiz do projeto:

```powershell
./scripts/k8s-local-deploy.ps1
kubectl port-forward --namespace docflow-staging service/docflow-backend 8080:8080
```

Em outro terminal, o smoke test pode ser executado contra `http://localhost:8080`.

Este ambiente é para validação local da Fase 1. Persistência, ingress, TLS, secrets
gerenciados e publicação em um cluster remoto permanecem decisões de uma etapa posterior.
