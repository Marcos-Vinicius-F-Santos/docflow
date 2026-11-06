package com.dockflow.dockflow.document.application.logging;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.dockflow.dockflow.document.security.TenantIdentityService;

/** Persists log events with eventId-level idempotency. */
@Service
public class ApplicationLogPersistenceService {

    private final ApplicationLogRepository repository;
    private final TenantIdentityService tenantIdentityService;

    public ApplicationLogPersistenceService(ApplicationLogRepository repository) {
        this.repository = repository;
        this.tenantIdentityService = null;
    }

    @Autowired
    public ApplicationLogPersistenceService(
        ApplicationLogRepository repository,
        TenantIdentityService tenantIdentityService
    ) {
        this.repository = repository;
        this.tenantIdentityService = tenantIdentityService;
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
            repository.saveAndFlush(new ApplicationLogEntity(event, currentTenantId()));
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

    private java.util.UUID currentTenantId() {
        if (tenantIdentityService == null) {
            return TenantIdentityService.BOOTSTRAP_TENANT_ID;
        }
        try {
            return tenantIdentityService.currentTenantId();
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            return TenantIdentityService.BOOTSTRAP_TENANT_ID;
        }
    }

    public enum PersistenceResult {
        PERSISTED,
        DUPLICATE
    }
}
