package dev.vepo.stomp4j.server.session;

@FunctionalInterface
public interface SessionCloser {

    void close(Session session);
}
