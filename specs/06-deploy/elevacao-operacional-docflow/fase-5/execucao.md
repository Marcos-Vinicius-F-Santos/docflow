# Execução — Fase 5: escala, carga e resiliência

Ambiente-alvo: staging isolado ou ambiente de teste aprovado.  
Status: bloqueado até ferramenta, carga, limites e janela de execução serem aprovados.

## Pré-condições

- [ ] Observabilidade disponível.
- [ ] Carga máxima, duração, concorrência e custo aprovados.
- [ ] Massa de dados e limpeza definidos.
- [ ] Alertas de saturação e rollback conhecidos.
- [ ] Nenhum teste será executado contra produção.

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

