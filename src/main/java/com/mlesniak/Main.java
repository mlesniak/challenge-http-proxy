package com.mlesniak;

public class Main {
    public static void main(String... args) throws Exception {
        var httpPort = 8989;
        var httpsPort = 8990;
        new ProxyServer(httpPort, httpsPort).start();
    }
}
