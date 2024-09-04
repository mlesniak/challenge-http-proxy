package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

record Response(HttpClient.Version version, int status, Map<String, List<String>> headers, InputStream body) {
    public byte[] getStatusLine() {
        var version = switch (this.version) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
        };
        // The third parameter, explaining the status code
        // is actually optional and thus omitted.
        return String.format("%s %d\n\n", version, status).getBytes(StandardCharsets.US_ASCII);
    }
}

// https://codingchallenges.substack.com/p/coding-challenge-51-http-forward
// curl --proxy "http://localhost:8989" "http://httpbin.org/ip"
// @mlesniak basic logging
public class Main {
    private static final Set<String> IGNORED_HEADERS = Set.of("host");

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

        try {
            var is = client.getInputStream();
            var os = client.getOutputStream();

            Request request = Request.from(is);

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

    private static Response processRequest(Request request) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().build()) {
            var clientRequest = HttpRequest.newBuilder(request.target());
            // For the time being, we ignore multivalued headers, i.e. headers
            // with the same name and multiple occurrences.
            var headers = request.headers().entrySet().stream()
                    .filter(es -> !IGNORED_HEADERS.contains(es.getKey().toLowerCase()))
                    .flatMap(es -> Stream.of(es.getKey(), es.getValue().getFirst()))
                    .toArray(String[]::new);
            clientRequest.headers(headers);

            var body = client.send(clientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new Response(body.version(), body.statusCode(), body.headers().map(), body.body());
        }
    }
}
