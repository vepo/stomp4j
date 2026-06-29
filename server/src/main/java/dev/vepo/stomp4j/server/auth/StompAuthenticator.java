package dev.vepo.stomp4j.server.auth;

public interface StompAuthenticator {
    boolean authenticate(Credentials credentials);
}
