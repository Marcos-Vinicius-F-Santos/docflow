# Feature Spec — Armazenamento binário de documentos

Status: Draft  
Owner:  
Created: 2026-09-08  
Last updated: 2026-09-08  
Related RFC:  
Related ADRs:  

## 1. Problema

O DocFlow atualmente registra os metadados de um documento, mas não armazena o conteúdo binário associado. Isso deixa o registro incompleto: o sistema conhece o nome, o tipo, o tamanho e o estado do documento, porém não consegue garantir que o conteúdo esteja persistido e associado ao registro correto.

O projeto definiu o MinIO como object storage, mas o fluxo de negócio ainda não utiliza esse armazenamento. Também não existe uma referência persistida que permita relacionar um documento do domínio ao conteúdo armazenado.

## 2. Objetivo

Permitir que o DocFlow armazene o conteúdo binário de um documento em object storage e mantenha uma associação confiável entre esse conteúdo e o documento registrado.

O fluxo deve preservar as invariantes atuais do domínio, não declarar sucesso antes da confirmação do armazenamento e tornar falhas de persistência do conteúdo observáveis para o consumidor e para a operação.

## 3. Usuários / Atores

- Cliente que envia um documento e seus metadados.
- DocFlow, responsável por validar e registrar a operação.
- Object storage configurado para o DocFlow, com MinIO como provider definido para esta feature.
- Operador responsável por acompanhar falhas e a saúde do armazenamento.

## 4. User Stories

### US-001 — Armazenar conteúdo

Como cliente do DocFlow, quero enviar o conteúdo binário junto dos metadados do documento, para que o documento fique efetivamente persistido e possa ser processado posteriormente.

### US-002 — Confirmar associação

Como cliente do DocFlow, quero que o documento registrado mantenha uma referência ao conteúdo armazenado, para que o sistema consiga identificar inequivocamente o conteúdo correspondente.

### US-003 — Conhecer o resultado da operação

Como cliente do DocFlow, quero saber quando o armazenamento foi concluído ou falhou, para não tratar um documento sem conteúdo como se estivesse pronto.

### US-004 — Operar falhas de storage

Como operador, quero identificar tentativas, sucessos e falhas de armazenamento, para diagnosticar indisponibilidade e inconsistências.

## 5. Requisitos Funcionais

### FR-001 — Receber conteúdo associado

O sistema deve aceitar o conteúdo binário de um documento juntamente com os metadados necessários para identificá-lo.

### FR-002 — Preservar invariantes do documento

O sistema deve rejeitar documentos que violem as invariantes existentes: nome original ausente ou em branco, tipo de conteúdo ausente ou em branco, ou tamanho negativo.

### FR-003 — Persistir o conteúdo

O sistema deve persistir o conteúdo binário aceito no object storage configurado para o DocFlow.

### FR-004 — Persistir a referência do conteúdo

Após o armazenamento confirmado, o sistema deve persistir uma referência estável que associe o documento do domínio ao conteúdo armazenado.

### FR-005 — Preservar integridade do conteúdo

O conteúdo armazenado deve corresponder integralmente ao conteúdo recebido, respeitando os metadados informados para tamanho e tipo de conteúdo.

### FR-006 — Não declarar sucesso prematuro

O sistema não deve declarar o documento como armazenado com sucesso antes de obter confirmação do object storage.

### FR-007 — Expor falha de armazenamento

Quando o object storage estiver indisponível, rejeitar o conteúdo ou não confirmar a operação, o sistema deve informar a falha ao consumidor e não deve apresentar o documento como armazenado com sucesso.

### FR-008 — Manter associação após conclusão

Uma vez concluído o armazenamento, consultas posteriores ao documento devem preservar a associação com o conteúdo armazenado até que uma regra explícita de retenção ou remoção seja aplicada.

### FR-009 — Evitar associação ambígua

Cada conteúdo armazenado deve possuir uma associação inequívoca com o documento correspondente, inclusive quando a operação for repetida após uma falha ou interrupção.

## 6. Regras de Negócio

- Um documento novo mantém o estado inicial `PENDING` durante o início do fluxo.
- O documento só pode ser considerado concluído para fins de armazenamento depois da confirmação do object storage.
- Falha ou ausência de confirmação do armazenamento não pode resultar em estado de sucesso.
- O tamanho informado deve representar o conteúdo binário enviado.
- O tipo de conteúdo informado deve permanecer associado ao conteúdo armazenado.
- O nome original é metadado do documento e não deve, por si só, determinar uma associação ambígua no storage.
- Credenciais do storage não fazem parte dos dados do documento nem podem ser expostas ao consumidor.
- A operação deve permitir distinguir uma falha antes do armazenamento de uma falha cujo resultado permaneceu inconclusivo.

## 7. Critérios de Aceitação

### AC-001 — Armazenamento bem-sucedido

**Requisitos relacionados:** FR-001, FR-003, FR-004, FR-006

**Dado** um documento com metadados válidos e conteúdo binário íntegro  
**Quando** o conteúdo for enviado ao DocFlow e o object storage confirmar a persistência  
**Então** o sistema deve informar sucesso e manter uma referência persistida para o conteúdo associado ao documento.

### AC-002 — Integridade do conteúdo

**Requisitos relacionados:** FR-003, FR-005

**Dado** um conteúdo binário aceito  
**Quando** a operação de armazenamento for concluída  
**Então** o conteúdo persistido deve ser idêntico ao conteúdo recebido e seus metadados de tamanho e tipo devem permanecer coerentes.

### AC-003 — Metadados inválidos

**Requisitos relacionados:** FR-002

**Dado** um envio com nome original ou tipo de conteúdo ausente/em branco, ou com tamanho negativo  
**Quando** o sistema validar a solicitação  
**Então** a operação deve ser rejeitada e nenhum sucesso de armazenamento deve ser informado.

### AC-004 — Tamanho declarado inconsistente

**Requisitos relacionados:** FR-005, FR-007

**Dado** um envio cujo tamanho declarado não corresponda ao conteúdo recebido  
**Quando** a inconsistência for detectada  
**Então** a operação deve falhar e o documento não deve ser apresentado como armazenado com sucesso.

### AC-005 — Object storage indisponível

**Requisitos relacionados:** FR-006, FR-007

**Dado** que o object storage esteja indisponível ou não confirme a persistência  
**Quando** o sistema tentar armazenar o conteúdo  
**Então** o consumidor deve receber uma indicação de falha, o documento não deve assumir sucesso e a ocorrência deve ser diagnosticável pela operação.

### AC-006 — Resultado inconclusivo

**Requisitos relacionados:** FR-007, FR-009

**Dado** que a comunicação seja interrompida após uma tentativa de armazenamento  
**Quando** o resultado da operação não puder ser confirmado  
**Então** o sistema não deve criar uma segunda associação ambígua nem declarar sucesso sem confirmação.

### AC-007 — Associação persistida

**Requisitos relacionados:** FR-004, FR-008, FR-009

**Dado** um documento cujo armazenamento tenha sido confirmado  
**Quando** o documento for consultado posteriormente  
**Então** a mesma associação com o conteúdo deve continuar identificável e não deve depender do nome original do arquivo.

### AC-008 — Observabilidade operacional

**Requisitos relacionados:** FR-003, FR-007

**Dado** uma tentativa de armazenamento  
**Quando** ela terminar com sucesso ou falha  
**Então** a operação deve produzir sinais suficientes para medir o resultado, a duração e a causa geral da falha sem expor credenciais ou o conteúdo do documento.

## 8. Edge Cases

- Conteúdo vazio com tamanho zero, considerando que o domínio atual permite tamanho não negativo.
- Conteúdo interrompido antes do fim do envio.
- Tamanho declarado maior ou menor que o conteúdo recebido.
- Tipo de conteúdo ausente, em branco ou incompatível com o conteúdo recebido.
- Nome original contendo caracteres especiais, separadores de caminho ou comprimento superior ao suportado pelo domínio.
- Object storage indisponível, lento ou com timeout.
- Falha ao persistir a referência do documento depois que o conteúdo já foi armazenado.
- Falha de comunicação depois que o storage pode ter concluído a operação.
- Repetição da mesma tentativa pelo cliente após timeout.
- Falha durante a consulta de confirmação do armazenamento.
- Conteúdo que exceda limites operacionais ainda não definidos.

## 9. Comportamento de Falha

- Erros de validação devem ser rejeitados sem tentativa de armazenamento.
- Falhas do object storage devem ser distinguíveis de erros de entrada.
- O consumidor não deve receber confirmação de sucesso quando o resultado do armazenamento for desconhecido.
- Falhas devem preservar informações suficientes para diagnóstico, sem registrar conteúdo binário ou credenciais.
- A estratégia para recuperar conteúdo órfão ou uma referência não persistida deve ser definida antes do release.

## 10. Requisitos Não Funcionais

### NFR-001 — Integridade

O sistema deve garantir que um armazenamento reportado como concluído corresponda ao conteúdo recebido, sem perda ou alteração silenciosa de bytes.

### NFR-002 — Consistência

Não deve existir um estado publicamente bem-sucedido sem uma associação válida entre o documento e o conteúdo armazenado.

### NFR-003 — Independência do provider

As regras de negócio do documento devem permanecer independentes dos detalhes específicos do MinIO ou de outro provider de object storage.

### NFR-004 — Segurança de credenciais

Credenciais e configurações sensíveis do storage devem ser externalizadas e nunca expostas em respostas, logs ou controle de versão.

### NFR-005 — Privacidade

Logs, métricas e traces não devem conter o conteúdo binário nem dados sensíveis desnecessários do documento.

### NFR-006 — Observabilidade

O fluxo deve permitir medir tentativas, sucessos, falhas, duração e indisponibilidade do storage, com correlação suficiente para investigação operacional.

### NFR-007 — Recuperabilidade

O comportamento para falhas parciais, conteúdo órfão e referências ausentes deve ser definido e verificável antes da disponibilização em produção.

### NFR-008 — Compatibilidade

As invariantes e os comportamentos já estabelecidos para registro e consulta de documentos não devem ser quebrados sem uma mudança explicitamente especificada.

## 11. Out of Scope

- Processamento assíncrono ou mensageria com RabbitMQ nesta feature.
- OCR, extração de texto, conversão, classificação ou análise do conteúdo.
- Download, compartilhamento público ou distribuição do documento.
- Versionamento, deduplicação ou retenção automática de documentos.
- Varredura antivírus ou validação semântica do conteúdo.
- Criação de uma nova interface de autenticação e autorização; os controles existentes continuam obrigatórios quando aplicáveis.
- Implementação de frontend.
- Substituição do MinIO por outro provider nesta feature.
- Definição de formato de endpoint, SDK, formato da referência do objeto, estratégia síncrona/assíncrona, provisionamento físico do bucket ou desenho detalhado de migration.

## 12. Dependências

- Object storage MinIO disponível nos ambientes suportados.
- Credenciais e configuração do storage fornecidas de forma segura.
- Persistência do documento disponível para manter a associação com o conteúdo.

## 13. Assumptions

- O conteúdo binário é recebido como parte da operação de registro do documento.
- O object storage é a fonte de persistência do conteúdo; o banco mantém os metadados e a associação necessária.
- Os estados atuais do documento continuam válidos: `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`.

## 14. Open Questions

- Qual limite máximo de tamanho deve ser suportado?
- O armazenamento deve aceitar conteúdo vazio?
- Qual política será aplicada quando o conteúdo for armazenado, mas a referência não puder ser persistida?
- Como será feita a reconciliação de operações cujo resultado ficou inconclusivo?
- Quais tipos de conteúdo serão permitidos ou bloqueados?
- Quais metas de latência, disponibilidade e taxa de sucesso serão adotadas?

## 15. Métricas de Sucesso

- Percentual de operações aceitas que terminam com conteúdo e referência persistidos; meta: 100% das operações reportadas como sucesso.
- Quantidade de documentos reportados como sucesso sem conteúdo confirmado; meta: zero.
- Quantidade de associações ambíguas ou referências duplicadas para uma mesma operação; meta: zero.
- Percentual de falhas de storage classificadas e correlacionáveis para diagnóstico; meta: 100%.
- Quantidade de conteúdo órfão detectado após falhas parciais; meta: zero ou tendência decrescente até a política de reconciliação ser definida.
- Latência de armazenamento e disponibilidade do object storage; metas quantitativas devem ser definidas antes do release.

## 16. Release Criteria

- Todos os requisitos `Must` e critérios de aceitação aplicáveis estão verificados.
- O conteúdo armazenado e a referência persistida permanecem consistentes em cenários de sucesso e falha.
- Não existem credenciais ou conteúdo sensível expostos em código, respostas ou observabilidade.
- Falhas de storage e falhas parciais possuem comportamento definido e operacionalmente verificável.
- As metas de capacidade, latência e disponibilidade foram definidas antes da liberação.
