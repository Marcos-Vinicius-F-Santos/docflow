package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.DocumentRepository;
import com.dockflow.dockflow.document.storage.DocumentStorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Wires the internal reconciliation use case without adding a global worker layer. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    name = "docflow.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DocumentReconciliationConfiguration {

    @Bean
    Clock reconciliationClock() {
        return Clock.systemUTC();
    }

    @Bean
    DocumentReconciliationService documentReconciliationService(
        DocumentRepository documentRepository,
        DocumentStorage documentStorage,
        Clock reconciliationClock,
        @Value("${docflow.reconciliation.max-attempts:5}") long maxAttempts,
        @Value("${docflow.reconciliation.backoff:PT1M,PT5M,PT15M,PT30M,PT60M}") String backoff
    ) {
        return new DocumentReconciliationService(
            documentRepository,
            documentStorage,
            maxAttempts,
            reconciliationClock,
            parseBackoff(backoff)
        );
    }

    private List<Duration> parseBackoff(String configuredBackoff) {
        if (configuredBackoff == null || configuredBackoff.isBlank()) {
            throw new IllegalArgumentException("docflow.reconciliation.backoff must not be empty");
        }

        try {
            return Arrays.stream(configuredBackoff.split(","))
                .map(String::trim)
                .map(Duration::parse)
                .collect(Collectors.toUnmodifiableList());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                "docflow.reconciliation.backoff must contain ISO-8601 durations",
                exception
            );
        }
    }
}
