package com.dockflow.dockflow.document.application;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Dispatches one event to independent notification channels. */
public class DocumentNotificationChannelDispatcher implements DocumentNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentNotificationChannelDispatcher.class);

    private final List<DocumentNotificationDispatcher> channels;

    public DocumentNotificationChannelDispatcher(List<DocumentNotificationDispatcher> channels) {
        this.channels = List.copyOf(channels);
    }

    @Override
    public void dispatch(DocumentLifecycleEvent event) {
        for (DocumentNotificationDispatcher channel : channels) {
            try {
                channel.dispatch(event);
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("eventId", event == null ? null : event.eventId())
                    .setCause(exception)
                    .log("document notification channel failed without affecting the document flow");
            }
        }
    }
}
