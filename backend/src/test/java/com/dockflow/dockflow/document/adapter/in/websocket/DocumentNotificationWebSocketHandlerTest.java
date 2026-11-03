package com.dockflow.dockflow.document.adapter.in.websocket;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentNotificationTestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentNotificationWebSocketHandlerTest {

    @Mock
    private WebSocketSession firstSession;

    @Mock
    private WebSocketSession secondSession;

    @Test
    void broadcastsThePublicEventToEveryOpenSession() throws Exception {
        when(firstSession.isOpen()).thenReturn(true);
        when(secondSession.isOpen()).thenReturn(true);
        DocumentNotificationWebSocketHandler handler = handler();
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);

        DocumentLifecycleEvent event = DocumentNotificationTestFixtures.lifecycleEvents().get(0);
        handler.dispatch(event);

        ArgumentCaptor<TextMessage> firstMessage = ArgumentCaptor.forClass(TextMessage.class);
        ArgumentCaptor<TextMessage> secondMessage = ArgumentCaptor.forClass(TextMessage.class);
        verify(firstSession).sendMessage(firstMessage.capture());
        verify(secondSession).sendMessage(secondMessage.capture());
        assertTrue(firstMessage.getValue().getPayload().contains("DOCUMENT_PROCESSING_STARTED"));
        assertTrue(firstMessage.getValue().getPayload().contains(event.eventId().toString()));
        assertFalse(firstMessage.getValue().getPayload().contains("docflow.document-events"));
        assertTrue(secondMessage.getValue().getPayload().contains(event.documentId().toString()));
    }

    @Test
    void ignoresClosedSessionsAndDoesNotRequireAClient() throws Exception {
        when(firstSession.isOpen()).thenReturn(false);
        DocumentNotificationWebSocketHandler handler = handler();
        handler.afterConnectionEstablished(firstSession);

        handler.dispatch(DocumentNotificationTestFixtures.lifecycleEvents().get(1));

        verify(firstSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void isolatesAClientDeliveryFailureFromOtherSessions() throws Exception {
        when(firstSession.isOpen()).thenReturn(true);
        when(secondSession.isOpen()).thenReturn(true);
        doThrow(new IOException("client disconnected")).when(firstSession).sendMessage(any(TextMessage.class));
        DocumentNotificationWebSocketHandler handler = handler();
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);

        handler.dispatch(DocumentNotificationTestFixtures.lifecycleEvents().get(2));

        verify(firstSession).sendMessage(any(TextMessage.class));
        verify(secondSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void removesAClosedConnectionFromTheBroadcastSet() throws Exception {
        DocumentNotificationWebSocketHandler handler = handler();
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionClosed(firstSession, org.springframework.web.socket.CloseStatus.NORMAL);

        handler.dispatch(DocumentNotificationTestFixtures.lifecycleEvents().get(3));

        verify(firstSession, never()).sendMessage(any(TextMessage.class));
    }

    private DocumentNotificationWebSocketHandler handler() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new DocumentNotificationWebSocketHandler(objectMapper);
    }
}
