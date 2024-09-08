package com.mlesniak;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;

public class Main {
    public static void main(String... args) throws Exception {
        // var port = 8989;
        // new ProxyServer(port).start();

        // curl --proxy-insecure --proxy "https://localhost:8989" "https://mlesniak.com"
        // keytool -genkeypair -alias proxy -keyalg RSA -keystore keystore.jks -keysize 2048
        // keytool -list -v -keystore keystore.jks
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());

        SSLContext sslctx = SSLContext.getInstance("TLS");
        sslctx.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory sslFac = sslctx.getServerSocketFactory();

        new Thread(() -> {
            ServerSocket sock = null;
            try {
                sock = sslFac.createServerSocket(8989);
                var client = sock.accept();
                // ProxyServer.handle(client);
                IOUtils.dump(client.getInputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        // Create a certificate:
        // keytool -exportcert -alias proxy -file servercert.crt -keystore keystore.jks
        //
        // Create a truststore or import it, respectively.
        // keytool -importcert -alias proxy -file servercert.crt -keystore truststore.jks
        //
        // Create an SSL socket and connect to the server
        System.setProperty("javax.net.ssl.trustStore", "truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
        Thread.sleep(1000);
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (Socket socket = sslSocketFactory.createSocket("localhost", 8989)) {
           socket.getOutputStream().write("Hello".getBytes());
        }
    }
}
