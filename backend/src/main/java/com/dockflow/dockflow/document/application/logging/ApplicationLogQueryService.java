package com.dockflow.dockflow.document.application.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Validates and queries only the paginated, read-only operational-log view. */
@Service
public class ApplicationLogQueryService {

    private static final int DEFAULT_SIZE = 50;
    private final ApplicationLogRepository repository;
    private final int maxPageSize;

    public ApplicationLogQueryService(
        ApplicationLogRepository repository,
        @Value("${docflow.logs.max-page-size:100}") int maxPageSize
    ) {
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("max page size must be positive");
        }
        this.repository = repository;
        this.maxPageSize = maxPageSize;
    }

    public ApplicationLogPageResponse find(
        String pageValue,
        String sizeValue,
        String levelValue,
        String service,
        String component,
        String operation,
        String documentIdValue,
        String correlationIdValue,
        String occurredFromValue,
        String occurredToValue
    ) {
        int page = parseNonNegativeInt(pageValue, "page", 0);
        int size = parseNonNegativeInt(sizeValue, "size", DEFAULT_SIZE);
        if (size < 1 || size > maxPageSize) {
            throw new ApplicationLogQueryValidationException("size must be between 1 and " + maxPageSize);
        }

        ApplicationLogLevel level = parseLevel(levelValue);
        UUID documentId = parseUuid(documentIdValue, "documentId");
        UUID correlationId = parseUuid(correlationIdValue, "correlationId");
        Instant occurredFrom = parseInstant(occurredFromValue, "occurredFrom");
        Instant occurredTo = parseInstant(occurredToValue, "occurredTo");
        if (occurredFrom != null && occurredTo != null && occurredTo.isBefore(occurredFrom)) {
            throw new ApplicationLogQueryValidationException("occurredTo cannot precede occurredFrom");
        }

        Specification<ApplicationLogEntity> specification = (root, query, criteriaBuilder) ->
            criteriaBuilder.conjunction();
        if (level != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("level"), level));
        }
        if (hasText(service)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("service"), service));
        }
        if (hasText(component)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("component"), component));
        }
        if (hasText(operation)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("operation"), operation));
        }
        if (documentId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("documentId"), documentId));
        }
        if (correlationId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("correlationId"), correlationId));
        }
        if (occurredFrom != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), occurredFrom));
        }
        if (occurredTo != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), occurredTo));
        }

        try {
            Page<ApplicationLogEntity> result = repository.findAll(
                specification,
                PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("eventId"))
                )
            );
            return new ApplicationLogPageResponse(
                result.getContent().stream().map(ApplicationLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
            );
        } catch (DataAccessException exception) {
            throw new ApplicationLogQueryUnavailableException(exception);
        }
    }

    private int parseNonNegativeInt(String value, String name, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ApplicationLogQueryValidationException(name + " must be a non-negative integer");
        }
    }

    private ApplicationLogLevel parseLevel(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return ApplicationLogLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApplicationLogQueryValidationException("level must be INFO, WARN or ERROR");
        }
    }

    private UUID parseUuid(String value, String name) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationLogQueryValidationException(name + " must be a UUID");
        }
    }

    private Instant parseInstant(String value, String name) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ApplicationLogQueryValidationException(name + " must be an ISO-8601 instant");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
