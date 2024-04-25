# Stomp4J

Stomp client for Java.

## Implemented Versions

This library can support all Stomp versions, but it was not test in others than 1.2

- [X] [STOMP Protocol Specification, Version 1.2](https://stomp.github.io/stomp-specification-1.2.html)
- [X] [STOMP Protocol Specification, Version 1.1](https://stomp.github.io/stomp-specification-1.1.html)
- [X] [STOMP Protocol Specification, Version 1.0](https://stomp.github.io/stomp-specification-1.0.html)

## Possible Clients

- [UK Network Rail](https://publicdatafeeds.networkrail.co.uk)
   ```java
   try (var client = new StompClient("ws://publicdatafeeds.networkrail.co.uk:61618", 
                                     new UserCredential(System.getenv("USERNAME"), System.getenv("PASSWORD")))) {
        client.connect();
        client.subscribe("/topic/TRAIN_MVT_ALL_TOC", data -> {
            // consume train data
        });
        client.join();
        client.unsubscribe("/topic/TRAIN_MVT_ALL_TOC");
   }
   ```

## Features

1. [X] Topic/Queue subscription
2. [X] Topic/Queue unsubscription
3. [ ] Send messages