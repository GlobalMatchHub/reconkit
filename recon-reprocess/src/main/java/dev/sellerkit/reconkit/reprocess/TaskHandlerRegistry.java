package dev.sellerkit.reconkit.reprocess;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers = new LinkedHashMap<>();

    public TaskHandlerRegistry register(TaskHandler handler) {
        handlers.put(handler.action(), handler);
        return this;
    }

    public TaskHandler require(String action) {
        TaskHandler handler = handlers.get(action);
        if (handler == null) {
            throw new IllegalArgumentException("no handler registered for action " + action);
        }
        return handler;
    }
}
