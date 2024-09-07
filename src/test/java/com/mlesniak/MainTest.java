package com.mlesniak;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class MainTest {
    private static final int port = (int)(Math.random() * (65536 - 1024) + 1024);

    @BeforeAll
    public static void setup() {
        Log.info("Using port {}", port);
    }

    @Test
    public void curl() throws IOException, InterruptedException {
        var proxyServer = spawnServer();
        var cmds = new String[]{
                "curl",
                "--proxy",
                "http://localhost:" + port,
                "http://httpbin.org/ip"
        };
        var p = Runtime.getRuntime().exec(cmds);
        var out = new String(p.getInputStream().readAllBytes());
        System.out.println(out);

        if (out.isEmpty()) {
            var err = new String(p.getErrorStream().readAllBytes());
            System.out.println(err);
        }

        proxyServer.stop();
    }

    private static ProxyServer spawnServer() throws InterruptedException {
        var ps = new ProxyServer(port);
        new Thread(() -> {
            try {
                ps.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(500);
        return ps;
    }
}
