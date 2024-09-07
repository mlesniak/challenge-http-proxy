package com.mlesniak;

import jdk.jshell.spi.ExecutionControl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
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
                Request request = Request.from(is, os);
                var response = processRequest(clientIp, request);
                if (response != null) {
                    // @mlesniak better TLD handling.
                    os.write(response.getStatusLine());
                    copy(response.body(), os);
                }
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
        if (request.isSSL()) {
            Log.info("Request {}", request);

            // Create a binary tunnel to the target and dispatch everything.
            var server = request.target().getScheme();
            var port = Integer.parseInt(request.target().getSchemeSpecificPart());
            var sock = new Socket(server, port);

            // Send back 200, and then simply print out everything we receive
            request.outputStream().write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

            // let's see what we receive then...
            var t1 =new Thread(() -> {
                int b;
                Log.info("Sending data");
                try {
                    while ((b = request.body().read()) != -1) {
                        sock.getOutputStream().write(b);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            t1.start();
            var t2 = new Thread(() -> {
                int b;
                Log.info("Receiving data");
                try {
                    while ((b = sock.getInputStream().read()) != -1) {
                        request.outputStream().write(b);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            t2.start();

            Log.info("Finished SSL");
            try {
                // @mlesniak this is bad, but we redesign anyway...
                t1.join();
                t2.interrupt();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

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
            var requestBody = request.method().equalsIgnoreCase("get") ?
                    HttpRequest.BodyPublishers.noBody() :
                    HttpRequest.BodyPublishers.ofInputStream(request::body);
            clientRequest.method(request.method(), requestBody);

            var body = client.send(clientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new Response(body.version(), body.statusCode(), body.headers().map(), body.body());
        } catch (InterruptedException e) {
            throw new IOException("Connection interrupted", e);
        }
    }
}
