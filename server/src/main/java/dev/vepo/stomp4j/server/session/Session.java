package dev.vepo.stomp4j.server.session;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuffer;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;
import dev.vepo.stomp4j.server.OutboundChannel;
import dev.vepo.stomp4j.server.channels.ChannelListener;

public class Session {
    private record Subscription(String topic, String id) {}

    private static final Logger logger = LoggerFactory.getLogger(Session.class);
    private final OutboundChannel channel;
    private final MessageBuffer buffer;
    private final ChannelListener listener;
    private final Set<Subscription> subscriptions;
    private Status status;

    public Session(OutboundChannel channel, ChannelListener listener) {
        this.channel = channel;
        this.buffer = new MessageBuffer();
        this.listener = listener;
        this.status = Status.STARTED;
        this.subscriptions = new HashSet<>();
    }

    public void offer(byte[] data, int length) {
        if (data.length > 0) {
            if (buffer.append(new String(data, 0, length))) {
                do {
                    process(buffer.message());
                } while (buffer.hasMessage());
            }
        }
    }

    private void process(Message message) {
        logger.info("Processing message {}", message);
        switch (status) {
            case STARTED:
                if (message.command() == Command.CONNECT) {
                    channel.send(MessageBuilder.builder(Command.CONNECTED)
                                               .header(Header.VERSION, "1.2").build());
                    status = Status.CONNECTED;
                } else {}
                break;
            case CONNECTED:
                switch (message.command()) {
                    case SEND:
                        logger.info("Received message: {}", message);
                        this.listener.messageReceived(message);
                        break;
                    case SUBSCRIBE:
                        logger.info("Subscribing: {}", message);
                        this.subscriptions.add(new Subscription(message.headers().destination().orElse(""),
                                                                message.headers().get(Header.ID).orElse("0")));
                        break;
                    default:
                        logger.warn("Command not implemented! {}", message);
                        break;
                }
            case END:
            default:
                throw new IllegalStateException("State not implemented!");
        }
    }

    public void handle(Message message) {
        logger.info("Handling message! {}", message);
        switch (message.command()) {
            case SEND:
                this.subscriptions.stream()
                                  .filter(s -> s.topic()
                                                .equals(message.headers()
                                                               .destination()
                                                               .orElseThrow(() -> new IllegalStateException("'destination' is a required header for a SEND command! command=%s".formatted(message)))))
                                  .forEachOrdered(subscription -> {
                                      this.channel.send(MessageBuilder.builder(Command.MESSAGE)
                                                                      .header(Header.SUBSCRIPTION, subscription.id())
                                                                      .header(Header.MESSAGE_ID, message.headers().get(Header.MESSAGE_ID).orElse(""))
                                                                      .header(Header.CONTENT_TYPE, message.headers().get(Header.CONTENT_TYPE).orElse(""))
                                                                      .body(message.body())
                                                                      .build());
                                  });
                break;

            default:
                break;
        }

    }

}
