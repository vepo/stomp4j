package dev.vepo.stomp4j.commons.protocol;

public enum Command {
    SEND,
    CONNECT,
    CONNECTED,
    SUBSCRIBE,
    MESSAGE,
    ERROR,
    ACK, 
    UNSUBSCRIBE,
    HEARTBEAT
}
