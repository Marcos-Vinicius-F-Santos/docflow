package com.dockflow.dockflow.document.application.logging;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists log events with eventId-level idempotency. */
@Service
public class ApplicationLogPersistenceService {

    private final ApplicationLogRepository repository;

    public ApplicationLogPersistenceService(ApplicationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PersistenceResult persist(ApplicationLogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        if (repository.existsById(event.eventId())) {
            return PersistenceResult.DUPLICATE;
        }

        try {
            repository.saveAndFlush(new ApplicationLogEntity(event));
            return PersistenceResult.PERSISTED;
        } catch (DataIntegrityViolationException exception) {
            // A concurrent consumer may have inserted the same event between
            // existsById and insert. The unique event_id is the idempotency gate.
            if (repository.existsById(event.eventId())) {
                return PersistenceResult.DUPLICATE;
            }
            throw exception;
        }
    }

    public enum PersistenceResult {
        PERSISTED,
        DUPLICATE
    }
}
