package com.mlesniak;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.security.KeyStore;

public class Main {
    public static void main(String... args) throws Exception {
        // var port = 8989;
        // new ProxyServer(port).start();

        // curl --proxy-insecure --proxy "https://localhost:8989" "https://mlesniak.com"
        // keytool -genkeypair -alias proxy -keyalg RSA -keystore keystore.jks -keysize 2048
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore.jks"), "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "password".toCharArray());

        SSLContext sslctx = SSLContext.getInstance("TLS");
        sslctx.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory sslFac = sslctx.getServerSocketFactory();

        ServerSocket sock = sslFac.createServerSocket(8989);
        var client = sock.accept();

        IOUtils.dump(client.getInputStream());
    }
}
