package com.dockflow.dockflow.document.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentReconciliationSchedulerTest {

    @Mock
    private ReconcileUnknownStorageResultUseCase reconciliation;

    @Test
    void shouldTriggerTheNextEligibleReconciliation() {
        DocumentReconciliationScheduler scheduler = new DocumentReconciliationScheduler(reconciliation);

        scheduler.reconcileNextDueDocument();

        verify(reconciliation).reconcileNextEligible();
    }

    @Test
    void shouldReleaseTheScheduledCycleWhenReconciliationFails() {
        doThrow(new IllegalStateException("storage unavailable"))
            .when(reconciliation)
            .reconcileNextEligible();
        DocumentReconciliationScheduler scheduler = new DocumentReconciliationScheduler(reconciliation);

        scheduler.reconcileNextDueDocument();

        verify(reconciliation).reconcileNextEligible();
    }
}
