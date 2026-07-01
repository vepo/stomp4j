module stomp4j.client {
    requires transitive stomp4j.commons;

    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports dev.vepo.stomp4j.client;
    exports dev.vepo.stomp4j.client.exceptions;
    exports dev.vepo.stomp4j.client.protocol;
    exports dev.vepo.stomp4j.client.protocol.v1_0;
    exports dev.vepo.stomp4j.client.protocol.v1_1;
    exports dev.vepo.stomp4j.client.protocol.v1_2;

    exports dev.vepo.stomp4j.client.internal to stomp4j.client.test;
    exports dev.vepo.stomp4j.client.internal.transport to stomp4j.client.test;

    opens dev.vepo.stomp4j.client.internal to stomp4j.client.test;
    opens dev.vepo.stomp4j.client.internal.transport to stomp4j.client.test;

    provides dev.vepo.stomp4j.client.transport.TransportProvider with dev.vepo.stomp4j.client.internal.transport.TcpTransportProvider,
            dev.vepo.stomp4j.client.internal.transport.WebSocketTransportProvider,
            dev.vepo.stomp4j.client.internal.transport.SecureTcpTransportProvider,
            dev.vepo.stomp4j.client.internal.transport.SecureWebSocketTransportProvider;

    provides dev.vepo.stomp4j.client.protocol.Stomp with dev.vepo.stomp4j.client.protocol.v1_0.Stomp1_0,
            dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1,
            dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;

    uses dev.vepo.stomp4j.client.transport.TransportProvider;
    uses dev.vepo.stomp4j.client.protocol.Stomp;
}
