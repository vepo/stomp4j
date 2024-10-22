module stomp4j {
    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports dev.vepo.stomp4j;
    exports dev.vepo.stomp4j.protocol;

    provides dev.vepo.stomp4j.protocol.TransportProvider with dev.vepo.stomp4j.protocol.transport.TcpTransportProvider,
            dev.vepo.stomp4j.protocol.transport.WebSocketTransportProvider;

    uses dev.vepo.stomp4j.protocol.TransportProvider;
}
