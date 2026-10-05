# Checklist de Convergência

Feature: Política de reconciliação para resultado desconhecido do storage (`RESULT_UNKNOWN`)

Data da revisão: 2026-09-14

Status: revisão realizada; deploy não aprovado.

Rodar antes de aprovar o deploy. A ideia é simples: o que a Spec promete, o que o Plano mandou fazer, o que o código faz e o que os testes realmente provaram precisam estar alinhados.

## Requisitos

- [ ] **Não confirmado integralmente.** O comportamento implementado corresponde aos FR-001 a FR-005 no núcleo da reconciliação, mas não existe no código atual um fluxo de gravação que produza `RESULT_UNKNOWN` e encaminhe o documento para a reconciliação. Os testes começam com o documento em `PROCESSING` e exercitam a reconciliação diretamente.
- [x] Os critérios de aceite AC-001, AC-002 e AC-004 estão refletidos no serviço e nos testes unitários: existência confirma `COMPLETED`, tentativas esgotadas levam a `FAILED` e tentativas restantes mantêm `PROCESSING`.
- [x] AC-003 está preservado: `RESULT_UNKNOWN` não é tratado como confirmação de gravação; somente `exists(objectKey) == true` leva a `COMPLETED`.
- [ ] **Não confirmado integralmente.** O FR-005 foi implementado com contador persistido na migration V4, porém a aplicação da migration e a leitura do schema não puderam ser validadas em integração porque o ambiente não tinha Docker disponível.

## Arquitetura

- [x] A lógica nova está em `document/application`, reutiliza a porta `DocumentStorage` existente e não cria endpoint, scheduler, consumer RabbitMQ ou nova abstração de storage.
- [x] A reconciliação apenas consulta `DocumentStorage.exists`; não repete a gravação e não chama `delete` após `FAILED`, conforme o escopo aprovado.
- [x] Não foi identificado cruzamento novo de limite arquitetural. A escolha de manter o serviço sem disparador/bean de execução é consciente: o componente que dispara a reconciliação está explicitamente fora desta feature.

## Dados

- [ ] **Não confirmado.** A migration V4 foi revisada estaticamente e é aditiva (`reconciliation_attempts BIGINT NOT NULL DEFAULT 0`), mas não foi executada contra PostgreSQL/Testcontainers nesta revisão.
- [x] Existe caminho de recuperação documentado em `docs/database.md`: manter a coluna adicional, voltar a uma versão compatível da aplicação e tratar a remoção da coluna somente em migration posterior.
- [x] O contador começa em zero para documentos existentes e novos, e a implementação não introduz histórico completo de tentativas, coerente com a decisão registrada na documentação.

## Testes

- [ ] **Não confirmado integralmente.** Os requisitos de prioridade alta têm cobertura unitária para a lógica principal: `DocumentReconciliationServiceTest` cobre os caminhos `PROCESSING`, `COMPLETED` e `FAILED`; `MinioDocumentStorageTest` cobre existência, ausência e falha inconclusiva. A persistência do V4 e o fluxo completo de `RESULT_UNKNOWN` não foram confirmados por integração.
- [x] Testes direcionados executados com sucesso: 20 testes, 0 falhas e 0 erros, incluindo serviço de reconciliação, adaptador MinIO e serviço de documentos.
- [ ] **Não confirmado integralmente.** Não há indício de regressão nos testes unitários/controladores executados, mas a regressão completa não foi concluída: os testes de integração e o contexto da aplicação falharam antes da execução por indisponibilidade do Docker/Testcontainers.

## Entrega

- [ ] **Não confirmado.** O procedimento de deploy ainda não foi executado nem validado em ambiente com PostgreSQL; a documentação registra a ordem esperada do Flyway, mas não há evidência operacional desta execução.
- [x] O rollback da migration é executável no sentido operacional documentado: interromper a promoção, voltar a uma versão compatível da aplicação e preservar a coluna V4. Não foi necessário alterar dados existentes nem usar rollback destrutivo.
- [x] T-015 foi marcado como concluído após a aprovação explícita do solicitante. Isso não representa aprovação do deploy.

## Divergências encontradas

1. **Fluxo produtor de `RESULT_UNKNOWN` ausente:** a Spec define o estado e a política de reconciliação, mas o fluxo binário que chama `store` e produz o resultado desconhecido continua fora do escopo. A implementação e os testes validam o serviço a partir de um documento já em `PROCESSING`; portanto, a transição completa desde a gravação não foi comprovada.

2. **`objectKey` é entrada da reconciliação:** o serviço recebe `documentId` e `objectKey`, pois o modelo atual só grava o `objectKey` ao concluir. Isso mantém a reconciliação verificável, mas deixa a origem/persistência da referência dependente do componente disparador, que pertence a outra Spec. É um risco já apontado no Plano e deve ser resolvido antes de depender de execução assíncrona real.

3. **Limite de tentativas sem valor de configuração:** a Spec deixa o número máximo fora do escopo. O serviço recebe `maxAttempts` por construção, mas não há configuração ou disparador que o forneça em produção; isso é esperado nesta fase, porém impede validar a operação completa.

4. **Compatibilidade do adaptador MinIO:** foi adicionada a dependência do SDK MinIO e ajustado o uso da API de exceção/status do adaptador existente para permitir a compilação e os testes do contrato `exists`. Não foi identificada mudança intencional na regra de negócio; a alteração é de compatibilidade da implementação existente.

5. **Integração bloqueada pelo ambiente:** a migration, a inicialização do contexto e a persistência PostgreSQL não puderam ser exercitadas porque o Docker daemon não estava disponível. Este item permanece aberto para a aprovação do deploy.

## Riscos e Registro de Decisão

- A ausência de `objectKey` persistido antes da conclusão e a definição futura do disparador podem exigir uma decisão de arquitetura quando a Spec do disparador for criada. **Merece Registro de Decisão:** sim, se a solução alterar o modelo de estado, a porta ou a coordenação entre componentes.
- A migration V4 é aditiva e o rollback documentado preserva compatibilidade. **Merece Registro de Decisão:** não nesta revisão; registrar somente se for escolhida remoção de coluna, histórico completo ou rollback destrutivo.

## Resultado da revisão

O núcleo da política está alinhado com os FR-001 a FR-005 e possui cobertura unitária direcionada. A convergência completa não pôde ser confirmada por causa da ausência do fluxo produtor/disparador, da dependência do `objectKey` fornecido ao serviço e da indisponibilidade do Docker para validar migration e integração. Este checklist não aprova o deploy.
