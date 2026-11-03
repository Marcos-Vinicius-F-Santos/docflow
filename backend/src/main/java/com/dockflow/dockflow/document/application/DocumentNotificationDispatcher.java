package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;

/** Application boundary for dispatching a lifecycle event to notification channels. */
@FunctionalInterface
public interface DocumentNotificationDispatcher {

    void dispatch(DocumentLifecycleEvent event);
}
