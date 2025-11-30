module stomp4j {
    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports dev.vepo.stomp4j;
    exports dev.vepo.stomp4j.protocol;
    exports dev.vepo.stomp4j.protocol.v1_0;
    exports dev.vepo.stomp4j.protocol.v1_1;
    exports dev.vepo.stomp4j.protocol.v1_2;

    provides dev.vepo.stomp4j.protocol.TransportProvider with dev.vepo.stomp4j.protocol.transport.TcpTransportProvider,
            dev.vepo.stomp4j.protocol.transport.WebSocketTransportProvider;

    uses dev.vepo.stomp4j.protocol.TransportProvider;
    uses dev.vepo.stomp4j.protocol.Stomp;
}
