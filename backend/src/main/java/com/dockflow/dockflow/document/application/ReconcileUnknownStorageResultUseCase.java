package com.dockflow.dockflow.document.application;

import java.util.UUID;

/**
 * Internal application entry point for reconciling an unknown storage result.
 *
 * The infrastructure trigger is implemented by the internal scheduler; this
 * contract intentionally exposes no REST or messaging API.
 */
public interface ReconcileUnknownStorageResultUseCase {

    void reconcile(UUID documentId);

    /**
     * Claims and reconciles the next due document, returning whether a document
     * was claimed by this execution.
     */
    boolean reconcileNextEligible();
}
