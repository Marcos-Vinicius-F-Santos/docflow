package com.dockflow.dockflow.document.controller;

import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogPageResponse;
import com.dockflow.dockflow.document.application.logging.ApplicationLogQueryService;
import com.dockflow.dockflow.document.application.logging.ApplicationLogQueryUnavailableException;
import com.dockflow.dockflow.document.application.logging.ApplicationLogQueryValidationException;
import com.dockflow.dockflow.document.application.logging.ApplicationLogResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationLogController.class)
class ApplicationLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationLogQueryService queryService;

    @Test
    void shouldReturnPaginatedLogsAndForwardAllApprovedFilters() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        when(queryService.find(
            "1", "25", "warn", "document-api", "controller", "document.upload",
            documentId.toString(), correlationId.toString(),
            "2026-10-29T10:00:00Z", "2026-10-29T11:00:00Z"
        )).thenReturn(new ApplicationLogPageResponse(
            List.of(new ApplicationLogResponse(
                eventId,
                1,
                Instant.parse("2026-10-29T10:30:00Z"),
                ApplicationLogLevel.WARN,
                "document-api",
                "controller",
                "document.upload",
                "Document accepted",
                correlationId,
                documentId,
                Map.of("filename", "example.pdf")
            )),
            1,
            25,
            26,
            2,
            false,
            true
        ));

        mockMvc.perform(get("/logs")
                .param("page", "1")
                .param("size", "25")
                .param("level", "warn")
                .param("service", "document-api")
                .param("component", "controller")
                .param("operation", "document.upload")
                .param("documentId", documentId.toString())
                .param("correlationId", correlationId.toString())
                .param("occurredFrom", "2026-10-29T10:00:00Z")
                .param("occurredTo", "2026-10-29T11:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(25))
            .andExpect(jsonPath("$.totalElements").value(26))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].level").value("WARN"))
            .andExpect(jsonPath("$.items[0].occurredAt").value("2026-10-29T10:30:00Z"))
            .andExpect(jsonPath("$.items[0].documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.items[0].details.filename").value("example.pdf"));

        verify(queryService).find(
            eq("1"), eq("25"), eq("warn"), eq("document-api"), eq("controller"),
            eq("document.upload"), eq(documentId.toString()), eq(correlationId.toString()),
            eq("2026-10-29T10:00:00Z"), eq("2026-10-29T11:00:00Z")
        );
    }

    @Test
    void shouldUseEmptyResultForDefaultQuery() throws Exception {
        when(queryService.find(null, null, null, null, null, null, null, null, null, null))
            .thenReturn(new ApplicationLogPageResponse(List.of(), 0, 50, 0, 0, true, true));

        mockMvc.perform(get("/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void shouldMapInvalidQueryToSafeProblemDetail() throws Exception {
        when(queryService.find(eq("0"), eq("101"), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null), eq(null)))
            .thenThrow(new ApplicationLogQueryValidationException("size must be between 1 and 100"));

        mockMvc.perform(get("/logs").param("page", "0").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid operational log query"))
            .andExpect(jsonPath("$.detail").value("size must be between 1 and 100"))
            .andExpect(jsonPath("$.errorCode").value("LOG_QUERY_INVALID"));
    }

    @Test
    void shouldMapDatabaseFailureToSafeServiceUnavailableProblemDetail() throws Exception {
        when(queryService.find(null, null, null, null, null, null, null, null, null, null))
            .thenThrow(new ApplicationLogQueryUnavailableException(new RuntimeException("database secret")));

        mockMvc.perform(get("/logs"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Operational logs unavailable"))
            .andExpect(jsonPath("$.detail").value("Operational logs are temporarily unavailable"))
            .andExpect(jsonPath("$.errorCode").value("LOG_QUERY_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database secret"))));
    }
}
