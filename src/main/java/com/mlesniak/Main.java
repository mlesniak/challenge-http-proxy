package com.mlesniak;

import java.io.IOException;

// https://codingchallenges.substack.com/p/coding-challenge-51-http-forward
// curl --proxy "http://localhost:8989" "http://httpbin.org/ip"
public class Main {
    public static void main(String... args) throws IOException {
        var port = 8989;
        new ProxyServer(port).start();
    }
}
