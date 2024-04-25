module stomp4j {
    requires java.base;
    requires java.net.http;
    requires java.naming;

    requires org.slf4j;
    requires org.apache.commons.codec;

    exports io.vepo.stomp4j;
    exports io.vepo.stomp4j.protocol;

    provides io.vepo.stomp4j.protocol.TransportProvider with io.vepo.stomp4j.protocol.transport.TcpTransportProvider,
            io.vepo.stomp4j.protocol.transport.WebSocketTransportProvider;

    uses io.vepo.stomp4j.protocol.TransportProvider;
}
