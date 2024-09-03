package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record Request(URI target, String header) {

}

record Response(HttpClient.Version version, int status, Map<String, List<String>> headers, InputStream body) {
    public byte[] getStatusLine() {
        var version = switch (this.version) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
        };
        // The third parameter, explaining the status code is
        // actually optional and thus omitted.
        return String.format("%s %d\n\n", version, status).getBytes(StandardCharsets.US_ASCII);
    }
}

// https://codingchallenges.substack.com/p/coding-challenge-51-http-forward
// curl --proxy "http://localhost:8989" "http://httpbin.org/ip"
// @mlesniak basic logging
public class Main {
    public static void main(String... args) throws IOException, InterruptedException {
        var port = 8989;
        startServer(port);
    }

    // @mlesniak  how to handle waiting indefinitely while allowing unit test curl calls?
    private static void startServer(int port) throws IOException, InterruptedException {
        var socket = new ServerSocket(port);
        System.out.printf("Start to listen on port %d%n", port);

        // while (true) {
        var client = socket.accept();
        processClient(client);
        // }
    }

    private static void processClient(Socket client) {
        System.out.printf("Client connected: %s%n", client.getInetAddress().getHostAddress());

        // @mlesniak error handling.
        try {
            var is = client.getInputStream();
            var os = client.getOutputStream();
            // @mlesniak combine to request
            var metadata = readMetadata(is);
            var request = determineTarget(metadata);

            var response = processRequest(request);
            os.write(response.getStatusLine());
            copy(response.body(), os);

            is.close();
            os.close();
            client.close();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        int b;
        while ((b = is.read()) != -1) {
            os.write(b);
        }
        is.close();
    }

    private static Response processRequest(Request target) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().build()) {
            var request = HttpRequest.newBuilder(target.target());

            // @mlesniak improve code quality
            // @mlesniak remove proxy connection
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
            return new Response(
                    is.version(),
                    is.statusCode(),
                    is.headers().map(),
                    is.body()
            );
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
