package com.dockflow.dockflow.document.adapter.in.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the native WebSocket endpoint used by the Hub. */
@Configuration
@EnableWebSocket
public class DocumentNotificationWebSocketConfiguration implements WebSocketConfigurer {

    public static final String ENDPOINT = "/ws/notifications";

    private final DocumentNotificationWebSocketHandler handler;
    private final String[] allowedOrigins;

    public DocumentNotificationWebSocketConfiguration(
        DocumentNotificationWebSocketHandler handler,
        @Value("${docflow.notification.websocket.allowed-origins:http://localhost:4200}") String[] allowedOrigins
    ) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, ENDPOINT).setAllowedOrigins(allowedOrigins);
    }
}
