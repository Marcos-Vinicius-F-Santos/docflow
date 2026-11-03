package com.dockflow.dockflow.document.adapter.in.websocket;

import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Broadcasts public document lifecycle events to all active Hub sessions. */
@Component
public class DocumentNotificationWebSocketHandler extends TextWebSocketHandler
    implements DocumentNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentNotificationWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public DocumentNotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.atWarn().addKeyValue("sessionId", session.getId()).setCause(exception)
            .log("document notification WebSocket transport failed");
    }

    @Override
    public void dispatch(DocumentLifecycleEvent event) {
        TextMessage message = new TextMessage(toJson(event));
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException | RuntimeException exception) {
                sessions.remove(session);
                log.atWarn().addKeyValue("sessionId", session.getId()).setCause(exception)
                    .log("document notification could not be delivered to WebSocket session");
            }
        }
    }

    private String toJson(DocumentLifecycleEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Document lifecycle event could not be serialized", exception);
        }
    }
}
