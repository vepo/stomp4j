package dev.vepo.stomp4j.quarkus.client;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD, FIELD, PARAMETER })
public @interface StompDestination {

    final class Literal extends AnnotationLiteral<StompDestination> implements StompDestination {

        private final String value;

        public Literal(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    String value();
}
