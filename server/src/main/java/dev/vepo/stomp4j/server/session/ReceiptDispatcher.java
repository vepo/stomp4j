package dev.vepo.stomp4j.server.session;

import dev.vepo.stomp4j.commons.protocol.Command;
import dev.vepo.stomp4j.commons.protocol.Header;
import dev.vepo.stomp4j.commons.protocol.Message;
import dev.vepo.stomp4j.commons.protocol.MessageBuilder;
import dev.vepo.stomp4j.server.OutboundChannel;

final class ReceiptDispatcher {

    static void sendReceipt(OutboundChannel channel, String receiptId) {
        channel.send(MessageBuilder.builder(Command.RECEIPT)
                                   .header(Header.RECEIPT_ID, receiptId)
                                   .build());
    }

    static void sendReceiptIfRequested(OutboundChannel channel, Message message) {
        message.headers().get(Header.RECEIPT).ifPresent(receiptId -> sendReceipt(channel, receiptId));
    }

    private ReceiptDispatcher() {}
}
