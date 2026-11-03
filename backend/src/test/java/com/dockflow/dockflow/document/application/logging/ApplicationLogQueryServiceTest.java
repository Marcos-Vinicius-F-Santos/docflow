package com.dockflow.dockflow.document.application.logging;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationLogQueryServiceTest {

    @Test
    void shouldUseApprovedDefaultsAndDescendingPageContract() {
        ApplicationLogRepository repository = mock(ApplicationLogRepository.class);
        when(repository.findAll(
            org.mockito.ArgumentMatchers.<Specification<ApplicationLogEntity>>any(),
            org.mockito.ArgumentMatchers.<Pageable>any()
        )).thenReturn(new PageImpl<ApplicationLogEntity>(List.of(), PageRequest.of(0, 50), 0));
        ApplicationLogPageResponse result = new ApplicationLogQueryService(repository, 100)
            .find(null, null, null, null, null, null, null, null, null, null);

        assertEquals(0, result.page());
        assertEquals(50, result.size());
        assertEquals(0, result.totalElements());
    }

    @Test
    void shouldRejectInvalidRangeAndPageSize() {
        ApplicationLogQueryService service = new ApplicationLogQueryService(mock(ApplicationLogRepository.class), 100);
        assertThrows(ApplicationLogQueryValidationException.class, () -> service.find(
            "0", "101", null, null, null, null, null, null, null, null
        ));
        assertThrows(ApplicationLogQueryValidationException.class, () -> service.find(
            "0", "50", null, null, null, null, null, null,
            "2026-10-29T12:00:00Z", "2026-10-29T11:00:00Z"
        ));
    }

    @Test
    void shouldMapDatabaseFailureToSafeUnavailableException() {
        ApplicationLogRepository repository = mock(ApplicationLogRepository.class);
        when(repository.findAll(
            org.mockito.ArgumentMatchers.<Specification<ApplicationLogEntity>>any(),
            org.mockito.ArgumentMatchers.<Pageable>any()
        )).thenThrow(new DataAccessResourceFailureException("db details"));

        ApplicationLogQueryUnavailableException exception = assertThrows(
            ApplicationLogQueryUnavailableException.class,
            () -> new ApplicationLogQueryService(repository, 100)
                .find("0", "50", null, null, null, null, null, null, null, null)
        );

        assertEquals("Operational logs are temporarily unavailable", exception.getMessage());
    }
}
