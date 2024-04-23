package io.vepo.stomp4j.protocol;

import java.util.HashMap;

public class MessageBuilder {
    private final Command command;
    private HashMap<Header, String> headers;

    public MessageBuilder(Command command) {
        this.command = command;
        this.headers = new HashMap<>();
    }

    public static MessageBuilder builder(Command command) {
        return new MessageBuilder(command);
    }

    public MessageBuilder header(Header acceptVersion, String acceptedVersions) {
        this.headers.put(acceptVersion, acceptedVersions);
        return this;
    }

    public String build() {
        var builder = new StringBuilder();
        builder.append(command.name())
               .append(Message.NEW_LINE);
        headers.forEach((key, value) -> builder.append(key.value())
                                               .append(Message.DELIMITER)
                                               .append(value)
                                               .append(Message.NEW_LINE));
        return builder.append(Message.NEW_LINE)
                      .append(Message.END)
                      .toString();
    }

}
