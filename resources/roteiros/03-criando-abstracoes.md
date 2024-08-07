# Design de Código com Stomp4J: Criando abstrações

Objetivo: mostrar como criar abstrações de forma responsável.

Para facilitar, vamos definir que os objetos/interfaces são nossas abstrações.

Tipos de Abstrações
* Interfaces -> Esteriótipo
* Classes -> Responsabilidades
* POJO: Plain Old Java Object
* Records

> Tell, don't ask

## Como caracterizar bons objetos

1. Fuja do Manager
    * Um objeto deve ter uma responsabilidade bem definida
2. Converse com e sobre seus objetos
4. Use os nomes do dóminio no seus objetos
5. Conheça alguns Design Patterns
    Nesse código uso
    * Observer (TcpListener) https://refactoring.guru/pt-br/design-patterns/observer
    * Decorator (Transport) https://refactoring.guru/pt-br/design-patterns/decorator
6. Não tente ser o mais abrangente
    * A ideia é facilitar o desenvolvimento e compreensão
    * Duplicação pode ser boa
7. A abstração deve encapsular a complexidade

## Passos para se criar abstrações de forma eficiente
### 1. Liste todas as responsabilidades do seu objeto

Exemplo TcpTransport:

Responsabilidades:
* Gerenciar a conexão socket TCP
* Ler mensagens do socket
* Enviar mensagens para o socket
* Informar o tempo de silêncio

### 2. Faça perguntas às responsabilidades

* Como eu envio mensagens?
    * A mensagem deve ser deserializida e escrita no socket
* Como eu recebo mensagens?
    * O fluxo de dados é recibido a ao ser identificada o fim de uma mensagem ela deve ser deserializada e informada ao cliente


Ao se fazer perguntas, as responsabilidades de alto nível serão detalhada em pequenas tarefas. Nesse caso identificamos:

* Serialização/Deserialização
* Buffer
* Identificação do ponto de corte
* Informar o Cliente StompClient

### 3. Associar responsabilidades com classes

* MessageBuffer
    * Buffer
    * Identificação do ponto de corte
* TransportListener e StompClient
    * Informar o Cliente StompClient
* Message
    * Deserialização
* MessageBuilder
    * Serialização