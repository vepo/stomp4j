# Programação Orientada a Objetos, Encapsulamento e Programação Funcional

## Programação Orientada a Objetos
Programação Orientada a Objetos torna Objetos como entidade principal da programação. Objeto é a estrutura que alia dados a comportamento.

Resumo por Michael Feathers: 
https://twitter.com/mfeathers/status/29581296216

_OO makes code understandable by encapsulating moving parts. FP makes code understandable by minimizing moving parts._

Orientação a Objetos faz o código ser compreensível encapsulando as partes móveis. Programação Funcional faz código compreensível minimizando as partes móveis.

Exemplo?

Classe Transport em StompClient.

Encapsula a lógica de conexão TCP e Web Socket criando uma interface única.

A classe StompClient não precisa se preocupar se uma conexão é TCP ou WebSocket. Há o encapsulamento dessa complexidade. A classe precisa criar um nível de abstração e separar responsabilidades. O StompClient pode conectar via TCP e WebSocket? Isso significa que deve se criar uma abstração para encapsular a complexidade da coneção.

Isso é um típico server socket.

```java
try {
    // Criando o socket do cliente para se conectar ao servidor
    Socket socket = new Socket(SERVER_ADDRESS, PORT);
    System.out.println("Conectado ao servidor " + SERVER_ADDRESS + " na porta " + PORT);

    // Criando fluxos de entrada e saída para comunicação com o servidor
    BufferedReader inputFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    PrintWriter outputToServer = new PrintWriter(socket.getOutputStream(), true);

    // Lendo entrada do usuário
    BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
    String userInputLine;

    // Lendo entrada do usuário e enviando para o servidor
    while ((userInputLine = userInput.readLine()) != null) {
        outputToServer.println(userInputLine);

        // Recebendo a resposta do servidor
        String serverResponse = inputFromServer.readLine();
        System.out.println("Resposta do servidor: " + serverResponse);
    }

    // Fechando conexão
    socket.close();
} catch (Exception e) {
    e.printStackTrace();
}
```

Isso é um típico WebSocket Client.

```java
HttpClient.newHttpClient()
          .newWebSocketBuilder()
          .buildAsync(uri, new WebSocket.Listener() {
                      @Override
                      public void onOpen(WebSocket webSocket) {
                            webSocketClient.sendText("Olá", true);
                            webSocketClient.request(1);
                      }

                      @Override
                      public void onError(WebSocket webSocket, Throwable error) {
                          System.err.println("Error on WebSocket connection! " + error.getMessage());
                      };

                      @Override
                      public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                          webSocket.request(1);
                          return null;
                      }

                      @Override
                      public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                          return null;
                      }
                  });
```

É preciso extrair o comportamento padrão.

```java
public interface Transport extends Closeable {

    void send(String content);

    void connect();

    String host();

    @Override
    void close();

    long silentTime();
}

public interface TransportListener {
    void onConnected(Transport transport);

    void onMessage(Message message);

    void onError(Message message);
}
```

Assim, OOP é alcançada quando se cria objetos com responsabilidades. Ao StompClient deve ter a responsabilidade de iniciar a coneção e prover uma interface simples encapsulando a complexidade do protocolo. O usuário não precisa entender os pormenores do protocolo.

### Encapsulando Complexidade

Responsabilidades do `StompClient`
* Abri conexão
* Enviar mensagens
* Ouvir mensagens do `Transport` implementando a interface `TransportListener`

Responsabilidades da interface `Transport`
* Implementar conexão
* Enviar mensagens recebidas ao `TransportListener`

### Questões de nomenclatura

Detalhes de implementação da classe são de responsabilidades da classe e devem ser encapsuladas na classe.
Se o nome dos métodos disserem EXATAMENTE o que método faz, há vazamento de complexidade.

O nome do método deve dizer aquilo que o método faz para fora da interface, ignorando a implementação.

## Programação Funcional

Ao invés de Objetos, Funções são o principal forma de definir o que será feito. 

É um paradigma declarativo e não imperativa. A lógica é construida através da declaração do que deve ser feitos.

Conceitos:

* Função Pura
* Imutabilidade
* Lazy-Evaluation
* Funções de primeira ordem
* Composicão de funções
* Recursão