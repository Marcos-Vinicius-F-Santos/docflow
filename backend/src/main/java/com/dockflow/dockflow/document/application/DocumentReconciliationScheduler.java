package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.application.logging.ApplicationLogContext;
import com.dockflow.dockflow.document.application.logging.ApplicationLogEventFactory;
import com.dockflow.dockflow.document.application.logging.ApplicationLogLevel;
import com.dockflow.dockflow.document.application.logging.ApplicationLogRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

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
    private Optional<ApplicationLogRecorder> operationalLogRecorder = Optional.empty();
    private ApplicationLogEventFactory operationalLogEventFactory = new ApplicationLogEventFactory();

    public DocumentReconciliationScheduler(ReconcileUnknownStorageResultUseCase reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Autowired
    void configureOperationalLogging(ApplicationLogRecorder recorder, ApplicationLogEventFactory eventFactory) {
        this.operationalLogRecorder = Optional.of(recorder);
        this.operationalLogEventFactory = eventFactory;
    }

    @Scheduled(fixedDelayString = "${docflow.reconciliation.schedule-interval:PT1M}")
    void reconcileNextDueDocument() {
        try {
            reconciliation.reconcileNextEligible();
        } catch (RuntimeException exception) {
            // The transaction that owns the row lock rolls back and releases it.
            // The next scheduled cycle can therefore retry the persisted candidate.
            LOGGER.warn("Reconciliation cycle failed; the candidate will remain eligible", exception);
            operationalLogRecorder.ifPresent(recorder -> recorder.record(operationalLogEventFactory.create(
                ApplicationLogLevel.ERROR,
                "document-api",
                "reconciliation-scheduler",
                "document.reconciliation.scheduler",
                "Reconciliation cycle failed",
                ApplicationLogContext.empty(),
                Map.of("exceptionType", exception.getClass().getSimpleName())
            )));
        }
    }
}
