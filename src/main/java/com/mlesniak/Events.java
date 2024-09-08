package com.mlesniak;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Very simple and pragmatic event system.
 */
public class Events {
    private static final Map<Event, List<Consumer<Object>>> listener = new HashMap<>();

    public enum Event {
        CLIENT_THREAD_STARTED,
        CLIENT_THREAD_STOPPED,
    }

    public static void add(Event key, Consumer<Object> action) {
        var eventListener = listener.get(key);
        if (eventListener == null) {
            eventListener = new LinkedList<>();
            eventListener.add(action);
            listener.put(key, eventListener);
        } else {
            eventListener.add(action);
        }
    }

    public static void emit(Event key, Object payload) {
        var eventListeners =listener.get(key);
        if (eventListeners == null) {
            return;
        }
        for (Consumer<Object> l : eventListeners) {
            l.accept(payload);
        }
    }

    public static void emit(Event key) {
        emit(key, null);
    }
}
