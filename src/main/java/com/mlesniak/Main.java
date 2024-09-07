package com.mlesniak;

import java.io.IOException;

public class Main {
    public static void main(String... args) throws IOException {
        var port = 8989;
        new ProxyServer(port).start();
    }
}
