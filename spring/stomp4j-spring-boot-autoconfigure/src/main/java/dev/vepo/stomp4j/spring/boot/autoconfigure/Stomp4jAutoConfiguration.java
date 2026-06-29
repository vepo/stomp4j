package dev.vepo.stomp4j.spring.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import dev.vepo.stomp4j.spring.boot.autoconfigure.client.StompClientAutoConfiguration;
import dev.vepo.stomp4j.spring.boot.autoconfigure.server.StompServerAutoConfiguration;

@AutoConfiguration
@Import({ StompClientAutoConfiguration.class, StompServerAutoConfiguration.class })
public class Stomp4jAutoConfiguration {}
