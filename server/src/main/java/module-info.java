module stomp4j.server {
    requires transitive stomp4j.commons;
    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;
    exports dev.vepo.stomp4j.server;
    exports dev.vepo.stomp4j.server.auth;
}
