package dev.vepo.stomp4j.client.exceptions;

public class StompException extends RuntimeException {

    public StompException(String message) {
        super(message);
    }

    public StompException(String message, Throwable cause) {
        super(message, cause);
    }

}
