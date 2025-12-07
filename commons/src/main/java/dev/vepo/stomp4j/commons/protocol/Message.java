package dev.vepo.stomp4j.commons.protocol;

import java.util.Objects;

public record Message(Command command, Headers headers, String body) {

    static final String DELIMITER = ":";
    static final String END = "\u0000";
    public static final String NEW_LINE = "\n";
    public static final Message HEARTBEAT = new Message(Command.HEARTBEAT);

    public Message(Command command, Headers headers) {
        this(command, headers, "");
    }

    public Message(Command command) {
        this(command, new Headers(), "");
    }

    public static Message readMessage(String content) {
        String[] splitMessage = content.split(Message.NEW_LINE);

        if (splitMessage.length == 0)
            throw new IllegalStateException("Did not received any message");

        String command = splitMessage[0];
        Headers stompHeaders = new Headers();
        StringBuilder body = new StringBuilder();

        int cursor = 1;
        for (int i = cursor; i < splitMessage.length; i++) {
            // empty line
            cursor = i;
            if (splitMessage[i].isEmpty()) {
                break;
            } else {
                var delimiterPos = splitMessage[i].indexOf(Message.DELIMITER);
                if (delimiterPos > 0) {
                    stompHeaders.add(splitMessage[i].substring(0, delimiterPos),
                                     splitMessage[i].substring(delimiterPos + 1));
                }
            }
        }

        for (int i = cursor + 1; i < splitMessage.length; i++) {
            body.append(splitMessage[i]);
        }

        if (body.isEmpty())
            return new Message(Command.valueOf(command), stompHeaders);
        else
            return new Message(Command.valueOf(command), stompHeaders, body.toString().replace(Message.END, ""));
    }

    public static String formatted(String message) {
        return message.replace(Message.END, "^@");
    }

    public String encode() {
        if (command == Command.HEARTBEAT) {
            return NEW_LINE;
        } else {
            var builder = new StringBuilder();
            builder.append(command.name())
                   .append(Message.NEW_LINE);
            headers.write(builder);
            builder.append(Message.NEW_LINE);
            if (Objects.nonNull(body)) {
                builder.append(body);
            }

            return builder.append(Message.END)
                          .toString();
        }
    }
}
