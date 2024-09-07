package com.mlesniak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class IOUtils {
    public static void close(InputStream is) {
        try {
            is.close();
        } catch (IOException e) {
            // Ignore.
        }
    }

    public static void close(OutputStream os) {
        try {
            os.close();
        } catch (IOException e) {
            // Ignore.
        }
    }

    public static void close(Socket client) {
        try {
            client.close();
        } catch (IOException e) {
            // Ignore.
        }
    }
}
