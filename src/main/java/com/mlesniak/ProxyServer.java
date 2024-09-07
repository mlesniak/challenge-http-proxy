package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class ProxyServer {
    private static final Set<String> IGNORED_HEADERS = Set.of(
            "host",
            "proxy-connection"
    );
    private final int port;
    private boolean run = true;

    public ProxyServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (var socket = new ServerSocket(port)) {
            Log.info("Start to listen on port {}", port);

            do {
                var client = socket.accept();

                // @mlesniak Use virtual threads.
                new Thread(() -> {
                    try {
                        Log.add("id", UUID.randomUUID().toString().split("-")[0]);
                        processClient(client);
                    } finally {
                        IOUtils.close(client);
                        Log.clear();
                    }
                }).start();
            } while (run);
        }
    }

    public void stop() {
        Log.info("Stopping server");
        run = false;
    }

    public static void processClient(Socket client) {
        var clientIp = client.getInetAddress().getHostAddress();
        Log.info("Client connected: {}", clientIp);

        try (var is = client.getInputStream()) {
            try (var os = client.getOutputStream()) {
                Request request = Request.from(is);
                var response = processRequest(clientIp, request);
                os.write(response.getStatusLine());
                copy(response.body(), os);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Log.info("Finished processing");
        }
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        int b;
        while ((b = is.read()) != -1) {
            os.write(b);
        }
        is.close();
    }

    private static Response processRequest(String clientIp, Request request) throws IOException {
        try (var client = HttpClient.newBuilder().build()) {
            var clientRequest = HttpRequest.newBuilder(request.target());
            // For the time being, we ignore multivalued headers, i.e. headers
            // with the same name and multiple occurrences. As long as we are
            // able to serve requests from Chrome, I am happy.
            String[] headers = Stream.concat(
                            request.headers().entrySet().stream()
                                    .filter(es -> !IGNORED_HEADERS.contains(es.getKey().toLowerCase()))
                                    .flatMap(es -> Stream.of(es.getKey(), es.getValue().getFirst())),
                            Stream.of("X-Forwarded-For", clientIp))
                    .toArray(String[]::new);
            clientRequest.headers(headers);

            var body = client.send(clientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new Response(body.version(), body.statusCode(), body.headers().map(), body.body());
        } catch (InterruptedException e) {
            throw new IOException("Connection interrupted", e);
        }
    }
}
