package com.dockflow.dockflow.document.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentFeatureContractFixturesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIXTURE_ROOT =
        "contracts/listagem-download-exclusao-documentos/";

    @Test
    void pageFixtureCoversTheApprovedCollectionContract() throws Exception {
        JsonNode page = read("document-page.json");

        assertEquals(1, page.get("page").asInt());
        assertEquals(10, page.get("size").asInt());
        assertEquals(4, page.get("content").size());
        assertEquals(4, page.get("totalElements").asInt());
        assertEquals(1, page.get("totalPages").asInt());

        Set<String> expectedFields = Set.of(
            "id",
            "originalFilename",
            "contentType",
            "sizeBytes",
            "status",
            "createdAt",
            "updatedAt"
        );
        Set<String> statuses = Set.of("PENDING", "PROCESSING", "COMPLETED", "FAILED");

        for (JsonNode document : page.get("content")) {
            assertEquals(expectedFields, fieldsOf(document));
        }

        assertEquals(
            statuses,
            java.util.stream.StreamSupport.stream(
                    java.util.Spliterators.spliteratorUnknownSize(
                        page.get("content").elements(),
                        java.util.Spliterator.ORDERED
                    ),
                    false
                )
                .map(document -> document.get("status").asText())
                .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void batchDownloadFixtureSeparatesIncludedAndSkippedDocuments() throws Exception {
        JsonNode batch = read("batch-download-selection.json");

        assertEquals(4, batch.get("documentIds").size());
        assertEquals(1, batch.get("includedInZip").size());
        assertEquals(3, batch.get("skippedDocumentIds").size());
        assertEquals("application/zip", batch.get("responseHeaders").get("Content-Type").asText());
        assertTrue(batch.get("responseHeaders").get("Content-Disposition").asText().contains("documents.zip"));
        assertEquals("no-store", batch.get("responseHeaders").get("Cache-Control").asText());
    }

    @Test
    void batchDeleteFixtureContainsPerItemPartialResults() throws Exception {
        JsonNode batch = read("batch-delete-result.json");

        assertEquals(3, batch.get("results").size());
        assertEquals("DELETED", batch.get("results").get(0).get("status").asText());
        assertEquals("FAILED", batch.get("results").get(1).get("status").asText());
        assertEquals("NOT_FOUND", batch.get("results").get(2).get("status").asText());
        assertNotNull(batch.get("results").get(1).get("errorCode"));
        assertNotNull(batch.get("results").get(2).get("errorCode"));
    }

    private static JsonNode read(String name) throws IOException {
        String resource = FIXTURE_ROOT + name;
        try (InputStream input = DocumentFeatureContractFixturesTest.class
            .getClassLoader()
            .getResourceAsStream(resource)) {
            assertNotNull(input, "Fixture not found: " + resource);
            return JSON.readTree(input);
        }
    }

    private static Set<String> fieldsOf(JsonNode document) {
        java.util.Set<String> fields = new java.util.HashSet<>();
        document.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
