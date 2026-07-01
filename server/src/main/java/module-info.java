module stomp4j.server {
    requires transitive stomp4j.commons;
    requires java.base;
    requires java.net.http;
    requires java.naming;
    requires io.vertx.core;
    requires io.vertx.web;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports dev.vepo.stomp4j.server;
    exports dev.vepo.stomp4j.server.auth;
    exports dev.vepo.stomp4j.server.channels to stomp4j.server.test;
    exports dev.vepo.stomp4j.server.session to stomp4j.server.test;

    opens dev.vepo.stomp4j.server.channels to stomp4j.server.test;
}
