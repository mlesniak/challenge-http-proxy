package com.mlesniak;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    // @mlesniak Use virtual threads.
    // @mlesniak still not clear, when a connection is finished and can be dropped.
    public void start() throws IOException {
        try (var socket = new ServerSocket(port)) {
            Log.info("Start to listen on port {}", port);
            var clientExecs = Executors.newFixedThreadPool(128);
            do {
                try {
                    Socket client = socket.accept();
                    clientExecs.submit(() -> {
                        try {
                            // Unique client id -- using the first characters of an
                            // uuid is sufficient for our toy proxy and does not
                            // pollute the log output as much.
                            String clientId = UUID.randomUUID().toString().split("-")[0];
                            Log.add("id", clientId);
                            handle(client);
                        } finally {
                            IOUtils.closeQuietly(client);
                            Log.clear();
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } while (run);
            clientExecs.shutdown();
        }
    }

    public void stop() {
        Log.info("Stopping server");
        run = false;
    }

    public static void handle(Socket client) {
        var clientIp = client.getInetAddress().getHostAddress();
        Log.info("Client connected: {}", clientIp);

        try (var is = client.getInputStream(); var os = client.getOutputStream()) {
            Request request = Request.from(is, os);
            switch (request.type()) {
                case HTTP -> handleUnencryptedRequest(clientIp, request);
                case HTTPS -> handleTunnel(request);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Log.info("Finished processing");
        }
    }

    private static void handleUnencryptedRequest(String clientIp, Request request) throws IOException {
        try (var client = HttpClient.newBuilder().build()) {
            var clientRequest = HttpRequest.newBuilder(request.target());
            // For the time being, we ignore multivalued headers, i.e. headers
            // with the same name and multiple occurrences. As long as we are
            // able to serve requests from Chrome, let's be happy.
            String[] headers = Stream.concat(
                            request.headers().entrySet().stream()
                                    .filter(es -> !IGNORED_HEADERS.contains(es.getKey().toLowerCase()))
                                    .flatMap(es -> Stream.of(es.getKey(), es.getValue().getFirst())),
                            Stream.of("X-Forwarded-For", clientIp))
                    .toArray(String[]::new);
            clientRequest.headers(headers);
            var requestBody = request.method().equalsIgnoreCase("get") ?
                    HttpRequest.BodyPublishers.noBody() :
                    HttpRequest.BodyPublishers.ofInputStream(request::body);
            clientRequest.method(request.method(), requestBody);

            var body = client.send(clientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            var response = new Response(body.version(), body.statusCode(), body.headers().map(), body.body());
            response.write(request.response());
        } catch (InterruptedException e) {
            throw new IOException("Connection interrupted", e);
        }
    }

    private static void handleTunnel(Request request) throws IOException {
        Log.info("Request {}", request);
        // Create a bidirectional tunnel to the target and let the client handle everything else.
        var server = request.target().getScheme();
        var port = Integer.parseInt(request.target().getSchemeSpecificPart());
        var sock = new Socket(server, port);

        // Send back 200 to signal that a tunnel has been established.
        request.response().write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

        var receiver = new Thread(() -> {
            int b;
            try {
                while ((b = sock.getInputStream().read()) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    request.response().write(b);
                }
            } catch (IOException e) {
                // This is ok-ish, sometimes the socket closes too fast.
                // While not perfect, good enough for now.
            }
        });
        receiver.start();

        var sender = new Thread(() -> {
            int b;
            try {
                while ((b = request.body().read()) != -1) {
                    sock.getOutputStream().write(b);
                }
                receiver.interrupt();
            } catch (IOException e) {
                // This is ok-ish, sometimes the socket closes too fast.
                // While not perfect, good enough for now.
            }
        });
        sender.start();

        Log.info("Finished HTTPS tunnel");
        try {
            sender.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
