package com.dockflow.dockflow.document.application.logging;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keeps operational log payloads safe before they reach a local logger or a
 * broker. It intentionally works on generic structures so callers cannot
 * accidentally serialize request, storage or exception internals.
 */
public final class ApplicationLogSanitizer {

    private static final Object REDACTED = "[REDACTED]";
    private static final Object REDACTED_BINARY = "[REDACTED_BINARY]";
    private static final Object REDACTED_EXCEPTION = "[REDACTED_EXCEPTION]";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
        "(?i)(password|passphrase|api[_-]?key|access[_-]?key|token|secret|authorization|credential|bucket)"
            + "\\s*[:=]\\s*([^,;\\s]+)"
    );
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
        "(?m)^\\s*at\\s+[^\\r\\n]+$"
    );

    private ApplicationLogSanitizer() {
    }

    public static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String withoutStackTrace = STACK_TRACE_LINE.matcher(message).replaceAll("[REDACTED_STACK_TRACE]");
        return SENSITIVE_ASSIGNMENT.matcher(withoutStackTrace)
            .replaceAll(match -> match.group(1) + "=[REDACTED]");
    }

    public static Map<String, Object> sanitizeDetails(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            String safeKey = key == null ? "null" : key;
            sanitized.put(safeKey, sanitizeValue(safeKey, value));
        });
        return Collections.unmodifiableMap(sanitized);
    }

    private static Object sanitizeValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Throwable) {
            return REDACTED_EXCEPTION;
        }
        if (value instanceof byte[] || value instanceof char[] || value.getClass().isArray()) {
            return REDACTED_BINARY;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                String safeKey = nestedKey == null ? "null" : String.valueOf(nestedKey);
                nested.put(safeKey, sanitizeValue(safeKey, nestedValue));
            });
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> nested = new ArrayList<>();
            for (Object item : iterable) {
                nested.add(sanitizeValue(key, item));
            }
            return Collections.unmodifiableList(nested);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
            || value instanceof java.time.temporal.TemporalAccessor
            || value instanceof java.util.UUID) {
            return value;
        }
        return sanitizeMessage(String.valueOf(value));
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.contains("password")
            || normalized.contains("passphrase")
            || normalized.contains("api_key")
            || normalized.contains("apikey")
            || normalized.contains("access_key")
            || normalized.contains("accesskey")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("authorization")
            || normalized.contains("credential")
            || normalized.equals("bucket")
            || normalized.endsWith("_bucket");
    }
}
