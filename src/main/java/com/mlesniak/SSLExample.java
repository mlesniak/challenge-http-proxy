package com.mlesniak;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;

/**
 * A small example how to establish SSL/TLS connections via sockets.
 *
 * For the server, create a private-public keypair via
 * <pre>
 * keytool -genkeypair -alias proxy -keyalg RSA -keystore keystore.jks -keysize 2048
 * </pre>
 *
 * and view the file with
 * <pre>
 * keytool -list -v -keystore keystore.jks
 * </pre>
 *
 * For the client, we need to create a truststore (which stores the public key
 * of the server).
 *
 * Create a certificate with
 * <pre>
 * keytool -exportcert -alias proxy -file servercert.crt -keystore keystore.jks
 * </pre>
 *
 * and import (or create) the truststore with
 * <pre>
 * keytool -importcert -alias proxy -file servercert.crt -keystore truststore.jks
 * </pre>
 *
 * For our demo, we expect our passwords to be "password".
 */
public class SSLExample {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore.jks"), "password".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());
        SSLContext sslctx = SSLContext.getInstance("TLS");
        sslctx.init(kmf.getKeyManagers(), null, null);
        SSLServerSocketFactory sslFac = sslctx.getServerSocketFactory();

        // Start a server in a background thread.
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
