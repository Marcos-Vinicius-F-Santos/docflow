# Execução — Fase 5: escala, carga e resiliência

Ambiente-alvo: staging isolado ou ambiente de teste aprovado.  
Status: execução concluída no staging kind; a validação autenticada no Render fica
como verificação manual opcional por depender de um JWT real de usuário Neon.

## Pré-condições

- [x] Observabilidade disponível.
- [ ] Carga máxima, duração, concorrência e custo aprovados.
- [ ] Massa de dados e limpeza definidos.
- [x] Alertas de saturação e rollback conhecidos.
- [x] Nenhum teste será executado contra produção.

## Passos

1. Registrar commit, imagem, configuração, ferramenta e parâmetros.
2. Executar smoke pequeno antes da carga.
3. Executar cenários de carga em ordem crescente.
4. Executar falhas controladas uma por vez, preservando logs e métricas.
5. Confirmar estados, duplicidade, retries e reconciliação.
6. Parar a carga se limites de custo, erro ou saturação forem atingidos.
7. Exportar relatório e artefatos sem segredos.
8. Limpar somente dados de teste identificados e permitidos.

## Rollback/recuperação

Parar geradores, recuperar dependências e promover a imagem anterior se necessário.
Não apagar mensagens, volumes ou objetos não identificados como dados de teste.

## Resultado obrigatório

Relatório medido, scripts versionados, evidência de falhas/recuperação e checklist aprovado.

## Validação atual

- Suíte completa do backend: `256` testes, `0` falhas, `0` erros e `0` ignorados.
- Staging kind: pods do backend, PostgreSQL, RabbitMQ e MinIO em `Running/Ready`;
  nenhum Job ou recurso temporário de validação ficou no namespace.
- Metrics Server instalado e `v1beta1.metrics.k8s.io` disponível; amostra sob carga
  registrou backend em `3–51m CPU` e `321–324Mi`, RabbitMQ em `636–906m CPU` e
  `144–197Mi`, Postgres em `22–38m CPU` e `63Mi`, e MinIO em `1m CPU` e `83Mi`.
- Contrato protegido validado no staging com JWKS efêmero: `GET /documents=200`,
  upload `201` e limpeza `204`; a validação contra o Render com JWT Neon real não
  foi executada porque o navegador remoto permaneceu offline.

