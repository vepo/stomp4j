package io.vepo.stomp4j.exceptions;

public class StompException extends RuntimeException {

    public StompException(String message) {
        super(message);
    }

    public StompException(String message, Throwable cause) {
        super(message, cause);
    }

}
