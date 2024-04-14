package io.vepo.stomp4j.protocol;

public enum Header {
    ACCEPT_VERSION("accept-version"),
    HOST("host"),
    HEART_BEAT("heart-beat"),
    LOGIN("login"),
    PASSCODE("passcode"),
    DESTINATION("destination"),
    ACK("ack"), 
    ID("id"),
    MESSAGE_ID("message-id");

    private final String value;

    Header(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
