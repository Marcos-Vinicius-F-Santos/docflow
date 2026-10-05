# Registro de Decisão — Organização por domínio, portas e adaptadores

Data: 2026-09-14  
Status: Aceita

## Contexto

O DocFlow precisa manter as regras de documentos separadas de HTTP, persistência,
mensageria e object storage. A organização atual ainda possui classes diretamente na
raiz do pacote `document`, enquanto a arquitetura definida pelo projeto prioriza
organização por domínio/feature.

## Decisão

O backend será organizado dentro do domínio `document`, separando domínio, aplicação,
portas e adaptadores. Controllers, repositórios, consumidores e integrações externas
permanecerão nas bordas; regras de negócio e contratos voltados à aplicação não
dependerão diretamente dos SDKs externos.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Organizar o projeto apenas por tipo (`controllers`, `services`, `repositories`) | Espalha uma feature por várias raízes e aumenta o acoplamento entre domínios conforme o projeto cresce. |
| Manter todas as classes na raiz do pacote `document` | Funciona no início, mas não deixa claros os limites entre domínio, aplicação e integrações. |
| Criar módulos separados para cada camada desde o início | Adiciona estrutura antes de haver conteúdo suficiente e contraria a regra de simplicidade incremental. |

## Consequências

A feature de documentos terá limites mais visíveis, testes mais direcionados e menor
acoplamento com Spring, RabbitMQ e MinIO nas regras de negócio. A organização também
facilita substituir adaptadores.

Como custo, a movimentação inicial de pacotes exige atualizar imports, configuração e
testes. Novas subdivisões continuam proibidas até que tenham conteúdo suficiente para
justificá-las.
