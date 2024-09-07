package com.mlesniak;

import java.io.Closeable;
import java.io.IOException;

public class IOUtils {
    public static void closeQuietly(Closeable is) {
        try {
            is.close();
        } catch (IOException e) {
            // Ignore.
        }
    }
}
