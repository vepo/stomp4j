module stomp4j {
    requires java.base;
    requires java.net.http;

    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires org.apache.commons.codec;

    exports io.vepo.stomp4j;
    exports io.vepo.stomp4j.protocol;
}
