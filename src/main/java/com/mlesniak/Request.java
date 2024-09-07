package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mlesniak.Request.Type.HTTP;
import static com.mlesniak.Request.Type.HTTPS;

record Request(String method, URI target, Map<String, List<String>> headers, InputStream body, OutputStream response) {
    public enum Type {
        HTTP,
        HTTPS
    }

    public Type type() {
        if (method.equalsIgnoreCase("connect")) {
            return HTTPS;
        }

        return HTTP;
    }

    public static Request from(InputStream is, OutputStream to) throws IOException {
        var preamble = readPreamble(is);
        return parse(preamble, is, to);
    }

    /**
     * Reads method, URI and all headers from the input stream, leaves
     * the rest to be consumed later (if there is anything at all).
     */
    private static String readPreamble(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        var buf = new int[4];
        int b;
        while ((b = is.read()) != -1) {
            // Could we also directly look at the StringBuilder?
            // Would this be faster?
            for (int i = 1; i < buf.length; i++) {
                buf[i - 1] = buf[i];
            }
            buf[buf.length-1] = b;

            // Newline followed by an empty line marks beginning of the body.
            if (buf[0] == '\r' && buf[1] == '\n' && buf[2] == '\r' && buf[3] == '\n') {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private static Request parse(String preamble, InputStream is, OutputStream to) {
        var lines = preamble.split("\r\n");
        if (lines.length < 1) {
            throw new IllegalArgumentException("No HTTP header found");
        }
        var lineParts = lines[0].split(" ");
        if (lineParts.length < 3) {
            throw new IllegalArgumentException("Invalid HTTP header:" + lines[0]);
        }
        var method = lineParts[0];
        var target = lineParts[1];
        // For now, we ignore the http version.

        Map<String, List<String>> headers = Arrays
                .asList(lines)
                .subList(1, lines.length - 1)
                .stream()
                .map(l -> l.split(":"))
                .map(ps -> {
                    if (ps.length < 2) {
                        throw new IllegalArgumentException("Illegal header: " + Arrays.asList(ps));
                    }
                    return Map.entry(ps[0], List.of(ps[1]));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Request(method, URI.create(target), headers, is, to);
    }
}
