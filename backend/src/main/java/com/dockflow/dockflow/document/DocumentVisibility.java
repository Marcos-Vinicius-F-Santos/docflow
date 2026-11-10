package com.dockflow.dockflow.document;

/** Controls who can discover and read a document. */
public enum DocumentVisibility {
    PRIVATE,
    PUBLIC;

    public static DocumentVisibility from(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("visibility must be PUBLIC or PRIVATE", exception);
        }
    }
}
