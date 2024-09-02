package com.mlesniak;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class MainTest {
    @Test
    public void curl() throws IOException, InterruptedException {
        spawnServer();
        var cmds = new String[]{
                "curl",
                "--proxy",
                "http://localhost:8989",
                "http://httpbin.org/ip"
        };
        var p = Runtime.getRuntime().exec(cmds);
        var out = new String(p.getInputStream().readAllBytes());
        System.out.println(out);

        if (out.isEmpty()) {
            var err = new String(p.getErrorStream().readAllBytes());
            System.out.println(err);
        }
    }

    private static void spawnServer() throws InterruptedException {
        new Thread(() -> {
            try {
                Main.main();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(500);
    }
}
