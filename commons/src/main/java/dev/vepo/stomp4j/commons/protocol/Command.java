package dev.vepo.stomp4j.commons.protocol;

public enum Command {
    SEND,
    CONNECT,
    STOMP,
    CONNECTED,
    SUBSCRIBE,
    MESSAGE,
    ERROR,
    ACK,
    NACK,
    RECEIPT,
    UNSUBSCRIBE,
    DISCONNECT,
    BEGIN,
    COMMIT,
    ABORT,
    HEARTBEAT
}
