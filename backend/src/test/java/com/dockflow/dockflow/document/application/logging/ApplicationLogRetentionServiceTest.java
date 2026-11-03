package com.dockflow.dockflow.document.application.logging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationLogRetentionServiceTest {

    @Test
    void shouldDeleteOnlyEventsOlderThanThreeHoursUsingTheInjectedClock() {
        ApplicationLogRepository repository = mock(ApplicationLogRepository.class);
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        when(repository.deleteByOccurredAtBefore(now.minus(Duration.ofHours(3)))).thenReturn(2L);
        ApplicationLogRetentionService service = new ApplicationLogRetentionService(
            repository,
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofHours(3)
        );

        assertEquals(2L, service.purgeExpiredLogs());
        verify(repository).deleteByOccurredAtBefore(now.minus(Duration.ofHours(3)));
    }
}
