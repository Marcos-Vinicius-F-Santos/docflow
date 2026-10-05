package com.dockflow.dockflow.test;

import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring integration test that owns PostgreSQL through IntegrationTestBase
 * and deliberately does not start external messaging or storage services.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = {
    "docflow.messaging.enabled=false",
    "spring.rabbitmq.username=test",
    "spring.rabbitmq.password=test",
    "docflow.storage.minio.endpoint=http://localhost:9000",
    "docflow.storage.minio.access-key=test-access",
    "docflow.storage.minio.secret-key=test-secret"
})
public @interface PostgresOnlyIntegrationTest {
}
