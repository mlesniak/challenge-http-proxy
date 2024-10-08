package com.mlesniak;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.mlesniak.Events.Event.CLIENT_THREAD_STARTED;
import static com.mlesniak.Events.Event.CLIENT_THREAD_STOPPED;

public class ProxyServer {
    private static final Set<String> IGNORED_HEADERS = Set.of(
            "host",
            "proxy-connection"
    );
    private final int httpPort;
    private final int httpsPort;

    public ProxyServer(int httpPort, int httpsPort) {
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
    }

    // We could use virtual threads here (or save this
    // for another playground project...).
    public void start() throws Exception {
        registerThreadLogging();

        var clientExecs = Executors.newFixedThreadPool(128);
        var sslSocket = createSslSocket(httpsPort);
        var socket = new ServerSocket(httpPort);

        startSocketListener(socket, clientExecs);
        startSocketListener(sslSocket, clientExecs);
    }

    private void startSocketListener(ServerSocket socket, ExecutorService clientExecs) {
        new Thread(() -> {
            Log.info("Start to listen on port {}", socket.getLocalPort());
            while (true) {
                try {
                    Socket client = socket.accept();
                    clientExecs.submit(() -> {
                        Events.emit(CLIENT_THREAD_STARTED);
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
                            Events.emit(CLIENT_THREAD_STOPPED);
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private ServerSocket createSslSocket(int httpsPort) throws Exception {
        // Usually, we would retrieve this from a secret
        // store, or at least an environment variable.
        var password = "password".toCharArray();

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore.jks"), password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, password);
        SSLContext sslctx = SSLContext.getInstance("TLS");
        sslctx.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory sslFac = sslctx.getServerSocketFactory();
        return sslFac.createServerSocket(httpsPort);
    }

    private void registerThreadLogging() {
        var counter = new AtomicInteger();
        Events.add(CLIENT_THREAD_STARTED, (_) -> {
            var running = counter.incrementAndGet();
            Log.info("Thread started: {}", running);
        });
        Events.add(CLIENT_THREAD_STOPPED, (_) -> {
            var running = counter.decrementAndGet();
            Log.info("Thread stopped: {}", running);
        });
    }

    public static void handle(Socket client) {
        var clientIp = client.getInetAddress().getHostAddress();
        Log.info("Client connected: {}", clientIp);

        try (var is = client.getInputStream(); var os = client.getOutputStream()) {
            Request request = Request.from(is, os);
            switch (request.type()) {
                case HTTP -> handleHttp(clientIp, request);
                case HTTPS -> handleHttps(request);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Log.info("Request processed");
        }
    }

    private static void handleHttp(String clientIp, Request request) throws IOException {
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

    private static void handleHttps(Request request) throws IOException {
        Log.info("Request {}", request);
        var server = request.target().getScheme();
        var port = Integer.parseInt(request.target().getSchemeSpecificPart());
        try (var sock = new Socket(server, port)) {
            var mdc = Log.get();
            var keepAlive = new AtomicBoolean(true);

            // Send back 200 to signal that a tunnel has been established.
            OutputStream response = request.response();
            response.write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

            Thread receiver = new Thread(() -> {
                Log.add(mdc);
                int count = 0;
                try {
                    int b;
                    while ((b = sock.getInputStream().read()) != -1) {
                        count++;
                        keepAlive.set(true);
                        response.write(b);
                    }
                } catch (IOException _) {
                    Log.info("Bytes received: " + count);
                }
            });

            Thread sender = new Thread(() -> {
                Log.add(mdc);
                int count = 0;
                try {
                    int b;
                    while ((b = request.body().read()) != -1) {
                        count++;
                        sock.getOutputStream().write(b);
                    }
                } catch (IOException _) {
                    Log.info("Bytes sent: " + count);
                }
            });

            ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(() -> {
                Log.add(mdc);
                if (!keepAlive.get()) {
                    // By closing the input stream, we stop
                    // the reading thread, and thus the join()
                    // below will succeed.
                    IOUtils.closeQuietly(request.body());
                }
                keepAlive.set(false);
            }, 5, 5, TimeUnit.SECONDS);

            receiver.start();
            sender.start();

            try {
                sender.join();
                // By closing the socket and request input stream,
                // we unblock stuck read operations, and we can
                // continue.
                IOUtils.closeQuietly(sock);
                IOUtils.closeQuietly(request.body());
                timer.shutdownNow();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
