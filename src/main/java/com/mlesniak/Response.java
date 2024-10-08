package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

record Response(HttpClient.Version version, int status, Map<String, List<String>> headers, InputStream body) {
    public byte[] statusLine() {
        var version = switch (this.version) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
        };
        // The third parameter, explaining the status code is optional and thus omitted.
        return String.format("%s %d\n\n", version, status).getBytes(StandardCharsets.US_ASCII);
    }

    public void write(OutputStream os) throws IOException {
        os.write(statusLine());
        IOUtils.copy(body(), os);
    }
}
