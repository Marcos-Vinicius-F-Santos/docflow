package com.dockflow.dockflow.document.adapter.in.websocket;

import com.dockflow.dockflow.document.application.DocumentNotificationChannelDispatcher;
import com.dockflow.dockflow.document.application.DocumentNotificationDispatcher;
import com.dockflow.dockflow.document.messaging.rabbitmq.RabbitMqDocumentEmailNotificationPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.Duration;
import java.util.List;

/** Composes WebSocket and e-mail as independent notification channels. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "docflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentNotificationChannelConfiguration {

    @Bean
    RabbitMqDocumentEmailNotificationPublisher documentEmailNotificationPublisher(
        RabbitTemplate rabbitTemplate,
        MessageConverter messageConverter,
        @Value("${docflow.notification.email.default-recipient:}") String recipient
    ) {
        return new RabbitMqDocumentEmailNotificationPublisher(
            rabbitTemplate,
            messageConverter,
            recipient,
            Duration.ofSeconds(3)
        );
    }

    @Bean
    @Primary
    DocumentNotificationDispatcher documentNotificationDispatcher(
        DocumentNotificationWebSocketHandler webSocketHandler,
        RabbitMqDocumentEmailNotificationPublisher emailPublisher
    ) {
        return new DocumentNotificationChannelDispatcher(List.of(webSocketHandler, emailPublisher));
    }
}
