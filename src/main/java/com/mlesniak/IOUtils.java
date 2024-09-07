package com.mlesniak;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtils {
    public static void closeQuietly(Closeable is) {
        try {
            is.close();
        } catch (IOException e) {
            // Ignore.
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        int b;
        while ((b = is.read()) != -1) {
            os.write(b);
        }
        is.close();
    }
}
