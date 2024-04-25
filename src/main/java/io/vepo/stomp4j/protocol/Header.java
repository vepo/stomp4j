package io.vepo.stomp4j.protocol;

public enum Header {
    ACCEPT_VERSION("accept-version"),
    ACK("ack"),
    DESTINATION("destination"),
    HEART_BEAT("heart-beat"),
    HOST("host"),
    ID("id"),
    LOGIN("login"),
    MESSAGE_ID("message-id"),
    PASSCODE("passcode"),
    SERVER("server"),
    SESSION("session"),
    VERSION("version"),
    CONTENT_TYPE("content-type"),
    SUBSCRIPTION("subscription");

    private final String value;

    Header(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
