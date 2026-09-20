package com.dockflow.dockflow.document.application;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentContentBoundaryTest {

    @Test
    void shouldExposeInputStreamAsTheInternalContentContract() {
        var components = Arrays.stream(DocumentContent.class.getRecordComponents())
            .toList();

        assertEquals(3, components.size());
        assertEquals(InputStream.class, components.get(2).getType());
        assertFalse(Arrays.stream(DocumentContent.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("getBytes")));
    }
}
