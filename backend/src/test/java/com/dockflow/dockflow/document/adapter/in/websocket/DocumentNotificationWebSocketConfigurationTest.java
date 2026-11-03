package com.dockflow.dockflow.document.adapter.in.websocket;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentNotificationWebSocketConfigurationTest {

    @Mock
    private WebSocketHandlerRegistry registry;

    @Test
    void registersTheApprovedNotificationsEndpoint() {
        org.mockito.MockitoAnnotations.openMocks(this);
        DocumentNotificationWebSocketHandler handler = new DocumentNotificationWebSocketHandler(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        );
        WebSocketHandlerRegistration registration = org.mockito.Mockito.mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, DocumentNotificationWebSocketConfiguration.ENDPOINT))
            .thenReturn(registration);

        new DocumentNotificationWebSocketConfiguration(handler, new String[] { "http://localhost:4200" })
            .registerWebSocketHandlers(registry);

        verify(registry).addHandler(eq(handler), eq("/ws/notifications"));
        verify(registration).setAllowedOrigins(eq("http://localhost:4200"));
    }
}
