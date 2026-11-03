package com.dockflow.dockflow.document.application.logging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Removes only operational logs older than the approved three-hour window. */
@Component
public class ApplicationLogRetentionService {

    public static final Duration DEFAULT_RETENTION = Duration.ofHours(3);

    private final ApplicationLogRepository repository;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public ApplicationLogRetentionService(
        ApplicationLogRepository repository,
        @Value("${docflow.logs.retention:PT3H}") Duration retention
    ) {
        this(repository, Clock.systemUTC(), retention);
    }

    public ApplicationLogRetentionService(ApplicationLogRepository repository, Clock clock, Duration retention) {
        if (repository == null || clock == null || retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("repository, clock and positive retention are required");
        }
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${docflow.logs.retention-schedule-interval:PT1M}")
    @Transactional
    public long purgeExpiredLogs() {
        return repository.deleteByOccurredAtBefore(Instant.now(clock).minus(retention));
    }
}
