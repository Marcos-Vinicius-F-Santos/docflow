# Execução — Fase 1: CI/CD próprio do DocFlow

Ambiente-alvo: GitHub Actions, GHCR e staging definido para o projeto.  
Status: bloqueado até Fase 0 aprovada e provedor de staging confirmado.

## Pré-condições

- [ ] Commit da Fase 0 aprovado.
- [ ] Secrets do GitHub configurados sem valores no repositório.
- [ ] Registry e ambiente de staging disponíveis.
- [ ] Permissão de publicação e deploy revisada.
- [ ] Rollback da imagem anterior identificável.

## Passos

1. Abrir pull request com o commit da fase.
2. Aguardar CI verde e salvar URL da execução.
3. Confirmar tag da imagem pelo SHA e guardar digest.
4. Executar deploy de staging somente pelo workflow aprovado.
5. Conferir Flyway, health, smoke de upload/processamento/consulta/download/exclusão.
6. Registrar logs, imagem, commit e URL do ambiente.
7. Executar rollback controlado para a imagem anterior e registrar resultado.
8. Reaplicar a versão aprovada somente após validação e autorização.

## Rollback

Promover a imagem anterior pelo digest registrado. Se o schema tiver migration incompatível,
parar e usar o procedimento de rollback da migration; não editar o banco manualmente.

## Resultado obrigatório

URL de CI, URL/digest da imagem, log de smoke, log de rollback e checklist aprovado.

