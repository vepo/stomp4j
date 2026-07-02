package dev.vepo.stomp4j.integration.client;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import dev.vepo.stomp4j.client.UserCredential;
import dev.vepo.stomp4j.commons.TransportType;

public record StompClientBootstrapOptions(String url,
                                          Optional<TransportType> transportType,
                                          Optional<UserCredential> credentials,
                                          Optional<SSLContext> sslContext) {}
