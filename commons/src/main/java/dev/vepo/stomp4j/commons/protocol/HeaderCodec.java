package dev.vepo.stomp4j.commons.protocol;

import java.util.Set;

public final class HeaderCodec {

    private static final Set<Command> UNESCAPED_COMMANDS = Set.of(Command.CONNECT, Command.CONNECTED, Command.STOMP);

    public static String decodeValue(Command command, String value) {
        return shouldEscape(command) ? unescape(value) : value;
    }

    public static String encodeValue(Command command, String value) {
        return shouldEscape(command) ? escape(value) : value;
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        var builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '\r' -> builder.append("\\r");
                case '\n' -> builder.append("\\n");
                case ':' -> builder.append("\\c");
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }

    public static boolean shouldEscape(Command command) {
        return !UNESCAPED_COMMANDS.contains(command);
    }

    public static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        var builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character != '\\') {
                builder.append(character);
                continue;
            }
            if (index + 1 >= value.length()) {
                throw new StompFrameException("Incomplete escape sequence in header value");
            }
            var next = value.charAt(++index);
            switch (next) {
                case 'r' -> builder.append('\r');
                case 'n' -> builder.append('\n');
                case 'c' -> builder.append(':');
                case '\\' -> builder.append('\\');
                default -> throw new StompFrameException("Undefined escape sequence \\%s in header value".formatted(next));
            }
        }
        return builder.toString();
    }

    private HeaderCodec() {}
}
