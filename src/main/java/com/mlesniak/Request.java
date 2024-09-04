package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Request(URI target, Map<String, List<String>> headers) {
    public static Request from(InputStream is) throws IOException {
        var preamble = readPreamble(is);
        return parse(preamble);
    }

    private static Request parse(String preamble) {
        var lines = preamble.split("\r\n");
        if (lines.length < 1) {
            throw new IllegalArgumentException("No HTTP header found");
        }
        var lineParts = lines[0].split(" ");
        if (lineParts.length < 3) {
            throw new IllegalArgumentException("Invalid HTTP header:" + lines[0]);
        }

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
        return new Request(URI.create(lineParts[1]), headers);
    }

    /**
     * Reads protocol, URI and all headers from the input stream, leaves
     * the rest to be consumed later.
     */
    private static String readPreamble(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        var buf = new int[4];
        var i = 0;
        int b;
        while ((b = is.read()) != -1) {
            buf[i++ % 4] = b;
            // Newline followed by an empty line marks beginning of the body.
            if (buf[0] == '\r' && buf[1] == '\n' && buf[2] == '\r' && buf[3] == '\n') {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }
}
