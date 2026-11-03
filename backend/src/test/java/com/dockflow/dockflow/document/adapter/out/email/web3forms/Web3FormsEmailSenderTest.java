package com.dockflow.dockflow.document.adapter.out.email.web3forms;

import com.dockflow.dockflow.document.adapter.out.email.EmailDeliveryException;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentNotificationTestFixtures;
import com.dockflow.dockflow.document.notification.FakeWeb3FormsServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Web3FormsEmailSenderTest {

    @Test
    void sendsTheEventUsingTheConfiguredAccessKeyAndReplyTo() throws Exception {
        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(200, "{\"success\":true}")) {
            Web3FormsEmailSender sender = new Web3FormsEmailSender(
                "backend-only-access-key",
                server.endpoint()
            );

            sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(1), "recipient@example.com");

            String body = server.requestBodies().getFirst();
            assertTrue(body.contains("backend-only-access-key"));
            assertTrue(body.contains("recipient@example.com"));
            assertTrue(body.contains("DOCUMENT_COMPLETED"));
            assertTrue(body.contains("Docflow"));
        }
    }

    @Test
    void acceptsTheProviderSuccessRedirectStatus() throws Exception {
        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(303, "")) {
            Web3FormsEmailSender sender = new Web3FormsEmailSender("key", server.endpoint());

            sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com");
        }
    }

    @Test
    void classifiesRateLimitAndServerErrorsAsTransient() throws Exception {
        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(429, "rate limited")) {
            Web3FormsEmailSender sender = new Web3FormsEmailSender("key", server.endpoint());
            EmailDeliveryException rateLimit = assertThrows(
                EmailDeliveryException.class,
                () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
            );
            assertEquals(EmailDeliveryException.FailureType.TRANSIENT, rateLimit.failureType());

            server.respondWith(503, "unavailable");
            EmailDeliveryException serverFailure = assertThrows(
                EmailDeliveryException.class,
                () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
            );
            assertEquals(EmailDeliveryException.FailureType.TRANSIENT, serverFailure.failureType());
        }
    }

    @Test
    void classifiesOtherClientErrorsAndProviderValidationAsPermanent() throws Exception {
        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(400, "invalid request")) {
            Web3FormsEmailSender sender = new Web3FormsEmailSender("key", server.endpoint());

            EmailDeliveryException failure = assertThrows(
                EmailDeliveryException.class,
                () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
            );

            assertEquals(EmailDeliveryException.FailureType.PERMANENT, failure.failureType());

            server.respondWith(200, "{\"success\":false,\"message\":\"invalid access key\"}");
            EmailDeliveryException providerFailure = assertThrows(
                EmailDeliveryException.class,
                () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
            );
            assertEquals(EmailDeliveryException.FailureType.PERMANENT, providerFailure.failureType());
        }
    }

    @Test
    void rejectsMissingBackendConfigurationWithoutCallingWeb3Forms() throws Exception {
        try (FakeWeb3FormsServer server = FakeWeb3FormsServer.start(200, "accepted")) {
            Web3FormsEmailSender sender = new Web3FormsEmailSender("", server.endpoint());

            EmailDeliveryException failure = assertThrows(
                EmailDeliveryException.class,
                () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
            );

            assertEquals(EmailDeliveryException.FailureType.PERMANENT, failure.failureType());
            assertTrue(server.requestBodies().isEmpty());
        }
    }

    @Test
    void classifiesHttpTimeoutAsTransient() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new HttpTimeoutException("timeout"));
        Web3FormsEmailSender sender = new Web3FormsEmailSender(
            httpClient,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            URI.create("http://web3forms.test/submit"),
            "key",
            Duration.ofMillis(50)
        );

        EmailDeliveryException failure = assertThrows(
            EmailDeliveryException.class,
            () -> sender.send(DocumentNotificationTestFixtures.lifecycleEvents().get(0), "recipient@example.com")
        );

        assertEquals(EmailDeliveryException.FailureType.TRANSIENT, failure.failureType());
    }
}
