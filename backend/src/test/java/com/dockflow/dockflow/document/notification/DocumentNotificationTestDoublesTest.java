package com.dockflow.dockflow.document.notification;

import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEventType;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentNotificationTestFixtures;
import com.dockflow.dockflow.document.messaging.rabbitmq.InMemoryDocumentLifecycleEventPublisher;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentNotificationTestDoublesTest {

    @Test
    void capturesTheFiveLifecycleEventsWithoutRabbitMq() {
        InMemoryDocumentLifecycleEventPublisher publisher = new InMemoryDocumentLifecycleEventPublisher();

        DocumentNotificationTestFixtures.lifecycleEvents().forEach(publisher::publish);

        assertEquals(5, publisher.publishedEvents().size());
        assertEquals(
            Set.of(DocumentLifecycleEventType.values()),
            publisher.publishedEvents().stream().map(DocumentLifecycleEvent::type).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void simulatesSuccessfulAndRateLimitedWeb3FormsResponsesLocally() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString("{\"event\":\"document.completed\"}");

        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(200, "{\"success\":true}")) {
            HttpResponse<String> accepted = client.send(
                request(server, body),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, accepted.statusCode());
            assertTrue(server.requestBodies().getFirst().contains("document.completed"));

            server.respondWith(429, "{\"code\":\"rate_limited\"}");
            HttpResponse<String> rateLimited = client.send(
                request(server, body),
                HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(429, rateLimited.statusCode());
            assertEquals(2, server.requestBodies().size());
        }
    }

    private static HttpRequest request(FakeWeb3FormsServer server, HttpRequest.BodyPublisher body) {
        return HttpRequest.newBuilder(server.endpoint())
            .header("content-type", "application/json")
            .POST(body)
            .build();
    }
}
