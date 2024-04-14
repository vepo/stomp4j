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
               .append("\n");
        headers.forEach((key, value) -> builder.append(key.value())
                                               .append(Stomp.DELIMITER)
                                               .append(value)
                                               .append(Stomp.NEW_LINE));
        return builder.append(Stomp.NEW_LINE)
                      .append(Stomp.END)
                      .toString();
    }

}
