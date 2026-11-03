package com.dockflow.dockflow.document.application.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Small bounded execution boundary for log publication. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ApplicationLogConfiguration {

    @Bean(name = "applicationLogExecutor", destroyMethod = "shutdown")
    Executor applicationLogExecutor() {
        return Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "docflow-application-log");
            thread.setDaemon(true);
            return thread;
        });
    }
}
