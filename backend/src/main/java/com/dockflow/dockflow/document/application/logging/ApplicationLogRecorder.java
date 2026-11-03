package com.dockflow.dockflow.document.application.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executor;

/** Records locally first and submits remote delivery without blocking callers. */
@Component
public class ApplicationLogRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationLogRecorder.class);

    private final ApplicationLogLocalWriter localWriter;
    private final Optional<ApplicationLogPublisher> publisher;
    private final Executor executor;

    public ApplicationLogRecorder(
        ApplicationLogLocalWriter localWriter,
        Optional<ApplicationLogPublisher> publisher,
        @Qualifier("applicationLogExecutor") Executor executor
    ) {
        this.localWriter = localWriter;
        this.publisher = publisher;
        this.executor = executor;
    }

    public void record(ApplicationLogEvent event) {
        if (event == null) {
            return;
        }

        try {
            localWriter.write(event);
        } catch (RuntimeException exception) {
            // Logging must never change the result of the business operation.
            LOG.atWarn().addKeyValue("eventId", event.eventId())
                .log("local operational log write failed");
        }
        publisher.ifPresent(remote -> submitRemote(remote, event));
    }

    private void submitRemote(ApplicationLogPublisher remote, ApplicationLogEvent event) {
        try {
            executor.execute(() -> {
                try {
                    remote.publish(event);
                } catch (RuntimeException exception) {
                    // The event is already durable in the local structured sink.
                    // Do not route this failure through ApplicationLogRecorder again.
                    LOG.atWarn().addKeyValue("eventId", event.eventId())
                        .log("remote operational log delivery failed; local event retained");
                }
            });
        } catch (RuntimeException exception) {
            // A saturated executor must not change the business operation result.
            LOG.atWarn().addKeyValue("eventId", event.eventId())
                .log("remote operational log scheduling failed; local event retained");
        }
    }
}
