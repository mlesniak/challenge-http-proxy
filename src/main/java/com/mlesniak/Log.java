package com.mlesniak;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class Log {
    public static ThreadLocal<SimpleDateFormat> sdf = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private Log() {
    }

    public static void info(String message, Object... args) {
        var m = message.replaceAll("\\{}", "%s");
        var f = now() + " " + m + "%n";
        System.out.printf(f, args);
    }

    private static String now() {
        return sdf.get().format(new Date());
    }
}
