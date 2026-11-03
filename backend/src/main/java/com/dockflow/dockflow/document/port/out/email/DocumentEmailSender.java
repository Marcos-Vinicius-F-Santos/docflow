package com.dockflow.dockflow.document.port.out.email;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;

/** Provider-neutral e-mail port for document lifecycle notifications. */
@FunctionalInterface
public interface DocumentEmailSender {

    void send(DocumentLifecycleEvent event, String recipient);
}
