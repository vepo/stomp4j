package dev.vepo.stomp4j.client;

public enum AckMode {
    AUTO("auto"),
    CLIENT("client"),
    CLIENT_INDIVIDUAL("client-individual");

    private final String wireValue;

    AckMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public boolean requiresManualAcknowledgement() {
        return this == CLIENT || this == CLIENT_INDIVIDUAL;
    }

    public String wireValue() {
        return wireValue;
    }
}
