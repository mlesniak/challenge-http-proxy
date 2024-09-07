package com.mlesniak;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class Log {
    public static ThreadLocal<SimpleDateFormat> sdf = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    private static final ThreadLocal<Map<String, Object>> mdc = ThreadLocal.withInitial(HashMap::new);

    private Log() {
    }

    public static void info(String message, Object... args) {
        var m = message.replaceAll("\\{}", "%s");
        var ctx = mdc.get().toString();
        var f = now() + " " + ctx + " " + m + "%n";
        System.out.printf(f, args);
    }

    public static void add(String key, String value) {
        mdc.get().put(key, value);
    }

    public static void clear() {
        mdc.get().clear();
    }

    private static String now() {
        return sdf.get().format(new Date());
    }
}
