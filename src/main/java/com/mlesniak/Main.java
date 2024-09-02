package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

record Request(URI target, String header) {

}

// https://codingchallenges.substack.com/p/coding-challenge-51-http-forward
// curl --proxy "http://localhost:8989" "http://httpbin.org/ip"
public class Main {
    public static void main(String... args) throws IOException, InterruptedException {
        var port = 8989;
        var socket = new ServerSocket(port);
        System.out.printf("Listening on port %d%n", port);

        // while (true) {
        System.out.println("Waiting for connection");
        var client = socket.accept();
        System.out.printf("Client connected: %s%n", client.getInetAddress().getHostAddress());
        var is = client.getInputStream();
        var os = client.getOutputStream();

        var metadata = readMetadata(is);
        var target = determineTarget(metadata);
        // @mlesniak pass rest of the payload.
        var targetIs = connect(target);
        int b;
        os.write("HTTP/1.1 200 OK\n\n".getBytes(StandardCharsets.UTF_8));
        while ((b = targetIs.read()) != -1) {
            os.write(b);
        }

        client.close();
        // }
    }

    private static InputStream connect(Request target) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().build()) {
            var request = HttpRequest.newBuilder(target.target());
            var headers = target.header().split("\r\n");
            for (String header : headers) {
                System.out.println(header);
                var ps = header.split(" ", 2);
                if (ps[0].equalsIgnoreCase("host:")) {
                    continue;
                }
                request.header(ps[0].substring(0, ps[0].length() - 1), ps[1]);
            }
            var r = request.build();
            var is = client.send(r, HttpResponse.BodyHandlers.ofInputStream());
            // @mlesniak pass complete response (headers, status code and payload)
            System.out.println(is.headers().toString());
            return is.body();
        }
    }

    private static Request determineTarget(String metadata) {
        var lines = metadata.split("\r\n");
        if (lines.length < 1) {
            throw new IllegalArgumentException("No HTTP header found");
        }
        var lineParts = lines[0].split(" ");
        if (lineParts.length < 3) {
            throw new IllegalArgumentException("Invalid HTTP target:" + lines[0]);
        }

        // Probably very expensive, but good enough for now.
        var headers = Arrays.asList(lines).subList(1, lines.length - 1)
                .stream().map(l -> l + "\r\n")
                .collect(Collectors.joining());
        return new Request(URI.create(lineParts[1]), headers);
    }

    private static String readMetadata(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        int newlines = 0;
        var buf = new int[4];
        var i = 0;
        while ((b = is.read()) != -1) {
            buf[i++ % 4] = b;
            if (buf[0] == '\r' && buf[1] == '\n' && buf[0] == buf[2] && buf[1] == buf[3]) {
                break;
            }
            sb.append((char) b);
        }

        return sb.toString();
    }
}
