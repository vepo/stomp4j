package dev.vepo.stomp4j.spring.boot.autoconfigure.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.vepo.stomp4j.client.AckMode;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StompListener {

    AckMode ackMode() default AckMode.AUTO;

    String destination();
}
