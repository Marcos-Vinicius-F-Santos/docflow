package com.dockflow.dockflow.document.application.logging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface ApplicationLogRepository
    extends JpaRepository<ApplicationLogEntity, UUID>, JpaSpecificationExecutor<ApplicationLogEntity> {

    long deleteByOccurredAtBefore(Instant cutoff);
}
