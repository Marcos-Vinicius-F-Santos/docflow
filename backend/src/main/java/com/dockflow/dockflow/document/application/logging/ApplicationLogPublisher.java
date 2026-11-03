package com.dockflow.dockflow.document.application.logging;

/** Outbound boundary for asynchronous operational log delivery. */
public interface ApplicationLogPublisher {

    void publish(ApplicationLogEvent event);
}
