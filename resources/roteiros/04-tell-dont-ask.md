# Design de Código com Stomp4J: Tell, Don't Ask

1. [X] Referências
    * [X] Seu maior erro na Orientação a Objetos! https://www.youtube.com/watch?v=X7BwBSqWw-U&t=8s
    * [X] Tell Dont Ask - Martin Fowler           https://martinfowler.com/bliki/TellDontAsk.html
    * [X] The Art of Enbugging                    https://media.pragprog.com/articles/jan_03_enbug.pdf
2. [X] "The Art of Enbugging"
    * [X] Uma das melhores maneiras de manter seu código livre de bugs é aplicar separação de responsabilidades
    * [X] Escrever código envergonhado
    * [X] Uma das principais distinções de OO é a ideia de enviar comando a entidades para fazer algo
    * [X] Procedural é sobre pegar informações e fazer decisões baseadas nessa informações
    * [X] Não devemos conhecer o estado interno do objeto
    * [X] Não devemos fazer decisões baseado no estado interno do objeto
    * [X] História do vendedor de jornais
3. [X] Vamos observar StompClient?
    * [X] Exemplo de uso
    ```java
    try (var client = new StompClient("ws://publicdatafeeds.networkrail.co.uk:61618", 
                                     new UserCredential(System.getenv("USERNAME"), System.getenv("PASSWORD")))) {
        client.connect();
        client.subscribe("/topic/TRAIN_MVT_ALL_TOC", data -> {
            // consume train data
        });
        client.join();
   }
    ```
    * [X] Responsabilidades da classe
    * [X] Interfaces: `connect`, `subscribe`, `join` e `unsubscribe`
4. [X] Vamos observar as classes Transport?
    * [X] Responsabilidades da classe
5. [X] Vamos observar as classes MessageBuffer?
    * [X] Responsabilidades da classe
