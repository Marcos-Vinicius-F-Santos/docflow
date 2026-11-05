package com.dockflow.dockflow.document.observability;

import org.slf4j.MDC;

import java.util.UUID;

/** Keeps one safe, request-scoped identifier available to HTTP, logs and messaging boundaries. */
public final class CorrelationIdContext {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MESSAGE_HEADER = "x-docflow-correlation-id";
    private static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static UUID currentUuid() {
        String value = current();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String normalizeOrGenerate(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // A malformed external value is replaced rather than copied to logs or messages.
            }
        }
        return UUID.randomUUID().toString();
    }

    public static void set(String value) {
        MDC.put(MDC_KEY, normalizeOrGenerate(value));
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
