module stomp4j.client {
    requires transitive stomp4j.commons;

    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports dev.vepo.stomp4j.client;
    exports dev.vepo.stomp4j.client.protocol;
    exports dev.vepo.stomp4j.client.protocol.v1_0;
    exports dev.vepo.stomp4j.client.protocol.v1_1;
    exports dev.vepo.stomp4j.client.protocol.v1_2;

    provides dev.vepo.stomp4j.client.transport.TransportProvider with dev.vepo.stomp4j.client.internal.transport.TcpTransportProvider,
                                                                      dev.vepo.stomp4j.client.internal.transport.WebSocketTransportProvider;

    provides dev.vepo.stomp4j.client.protocol.Stomp with dev.vepo.stomp4j.client.protocol.v1_0.Stomp1_0,
                                                         dev.vepo.stomp4j.client.protocol.v1_1.Stomp1_1,
                                                         dev.vepo.stomp4j.client.protocol.v1_2.Stomp1_2;
    uses dev.vepo.stomp4j.client.transport.TransportProvider;
    uses dev.vepo.stomp4j.client.protocol.Stomp;
}
