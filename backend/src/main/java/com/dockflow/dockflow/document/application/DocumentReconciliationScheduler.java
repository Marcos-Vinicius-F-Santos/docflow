package com.dockflow.dockflow.document.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically starts one atomically claimed reconciliation attempt. */
@Component
@ConditionalOnProperty(
    name = "docflow.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DocumentReconciliationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentReconciliationScheduler.class);

    private final ReconcileUnknownStorageResultUseCase reconciliation;

    public DocumentReconciliationScheduler(ReconcileUnknownStorageResultUseCase reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${docflow.reconciliation.schedule-interval:PT1M}")
    void reconcileNextDueDocument() {
        try {
            reconciliation.reconcileNextEligible();
        } catch (RuntimeException exception) {
            // The transaction that owns the row lock rolls back and releases it.
            // The next scheduled cycle can therefore retry the persisted candidate.
            LOGGER.warn("Reconciliation cycle failed; the candidate will remain eligible", exception);
        }
    }
}
