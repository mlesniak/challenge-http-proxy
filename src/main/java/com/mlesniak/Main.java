package com.mlesniak;

import java.io.IOException;

// @mlesniak add event system for stats
public class Main {
    public static void main(String... args) throws IOException {
        var port = 8989;
        new ProxyServer(port).start();
    }
}
