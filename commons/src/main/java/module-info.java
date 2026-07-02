module stomp4j.commons {
    requires java.base;
    requires org.slf4j;

    exports dev.vepo.stomp4j.commons;
    exports dev.vepo.stomp4j.commons.protocol;
    exports dev.vepo.stomp4j.commons.nio to stomp4j.client, stomp4j.server;
}
