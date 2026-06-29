package dev.vepo.stomp4j.commons.protocol;

public enum Header {
    ACCEPT_VERSION("accept-version"),
    ACK("ack"),
    DESTINATION("destination"),
    HEART_BEAT("heart-beat"),
    HOST("host"),
    ID("id"),
    LOGIN("login"),
    MESSAGE("message"),
    MESSAGE_ID("message-id"),
    PASSCODE("passcode"),
    RECEIPT("receipt"),
    RECEIPT_ID("receipt-id"),
    SERVER("server"),
    SESSION("session"),
    VERSION("version"),
    CONTENT_TYPE("content-type"),
    SUBSCRIPTION("subscription"), 
    CONTENT_LENGTH("content-length");

    private final String value;

    Header(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
