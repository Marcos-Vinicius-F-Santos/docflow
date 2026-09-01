package com.dockflow.dockflow;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class IntegrationTestBase {

    @ServiceConnection
    protected static final PostgreSQLContainer postgres;

    static {
        postgres = new PostgreSQLContainer("postgres:18-alpine");
        postgres.start();
    }
}