package dev.vepo.stomp4j.commons.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

final class FrameDecoder {

    private record Line(String content, int nextPosition) {}

    static Message decode(byte[] content) {
        return decode(content, 0, content.length);
    }

    static Message decode(byte[] content, int offset, int length) {
        if (length <= 0) {
            throw new StompFrameException("Did not receive any message");
        }
        var position = offset;
        var commandLine = readLine(content, position, offset + length);
        if (commandLine.content().isEmpty()) {
            throw new StompFrameException("Missing STOMP command");
        }
        position = commandLine.nextPosition();
        var command = Command.valueOf(commandLine.content().trim());
        var headers = new Headers();
        while (position < offset + length) {
            var headerLine = readLine(content, position, offset + length);
            position = headerLine.nextPosition();
            if (headerLine.content().isEmpty()) {
                break;
            }
            var delimiterPos = headerLine.content().indexOf(Message.DELIMITER);
            if (delimiterPos > 0) {
                var key = headerLine.content().substring(0, delimiterPos);
                var rawValue = headerLine.content().substring(delimiterPos + 1);
                headers.add(key, HeaderCodec.decodeValue(command, rawValue));
            }
        }
        var body = readBody(content, position, offset + length, headers);
        return body.isEmpty() ? new Message(command, headers) : new Message(command, headers, body);
    }

    static Message decode(String content) {
        return decode(content.getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Integer> parseContentLength(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            throw new StompFrameException("Invalid content-length header: %s".formatted(value));
        }
    }

    private static String readBody(byte[] content, int position, int end, Headers headers) {
        var contentLength = headers.get(Header.CONTENT_LENGTH)
                                   .flatMap(FrameDecoder::parseContentLength);
        if (contentLength.isPresent()) {
            var length = contentLength.get();
            if (position + length > end) {
                throw new StompFrameException("Incomplete frame body: expected %d octets".formatted(length));
            }
            return new String(content, position, length, StandardCharsets.UTF_8);
        }
        if (position >= end) {
            return "";
        }
        var body = new String(content, position, end - position, StandardCharsets.UTF_8).replace(Message.END, "");
        return stripOptionalTrailingLineEnding(body);
    }

    private static Line readLine(byte[] content, int position, int end) {
        var lineStart = position;
        var lineEnd = position;
        while (lineEnd < end && content[lineEnd] != '\n') {
            lineEnd++;
        }
        var lineContentEnd = lineEnd;
        if (lineContentEnd > lineStart && content[lineContentEnd - 1] == '\r') {
            lineContentEnd--;
        }
        var line = lineEnd < end
                                 ? new String(content, lineStart, lineContentEnd - lineStart, StandardCharsets.UTF_8)
                                 : new String(content, lineStart, end - lineStart, StandardCharsets.UTF_8);
        var nextPosition = lineEnd < end ? lineEnd + 1 : end;
        return new Line(line, nextPosition);
    }

    private static String stripOptionalTrailingLineEnding(String body) {
        if (body.endsWith("\r\n")) {
            return body.substring(0, body.length() - 2);
        }
        if (body.endsWith("\n") || body.endsWith("\r")) {
            return body.substring(0, body.length() - 1);
        }
        return body;
    }

    private FrameDecoder() {}
}
