package com.dockflow.dockflow.document.adapter.out.email.web3forms;

import com.dockflow.dockflow.document.adapter.out.email.EmailDeliveryException;
import com.dockflow.dockflow.document.messaging.rabbitmq.DocumentLifecycleEvent;
import com.dockflow.dockflow.document.port.out.email.DocumentEmailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Web3Forms HTTP adapter; the access key never crosses the provider-neutral port. */
public class Web3FormsEmailSender implements DocumentEmailSender {

    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.web3forms.com/submit");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String accessKey;
    private final Duration requestTimeout;

    public Web3FormsEmailSender(String accessKey) {
        this(accessKey, DEFAULT_ENDPOINT);
    }

    public Web3FormsEmailSender(String accessKey, URI endpoint) {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            new ObjectMapper().findAndRegisterModules(),
            endpoint,
            accessKey,
            Duration.ofSeconds(10)
        );
    }

    public Web3FormsEmailSender(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI endpoint,
        String accessKey,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void send(DocumentLifecycleEvent event, String recipient) {
        validate(event, recipient);
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(event, recipient)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            classifyResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException(
                EmailDeliveryException.FailureType.TRANSIENT,
                "Web3Forms request was interrupted",
                exception
            );
        } catch (IOException exception) {
            throw new EmailDeliveryException(
                EmailDeliveryException.FailureType.TRANSIENT,
                "Web3Forms request failed",
                exception
            );
        }
    }

    private void validate(DocumentLifecycleEvent event, String recipient) {
        if (event == null) {
            throw new EmailDeliveryException(EmailDeliveryException.FailureType.PERMANENT, "event is required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new EmailDeliveryException(
                EmailDeliveryException.FailureType.PERMANENT,
                "notification recipient is not configured"
            );
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new EmailDeliveryException(
                EmailDeliveryException.FailureType.PERMANENT,
                "Web3Forms access key is not configured"
            );
        }
    }

    private String requestBody(DocumentLifecycleEvent event, String recipient) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("access_key", accessKey);
        root.put("subject", "Docflow — " + event.type());
        root.put("from_name", "Docflow");
        root.put("email", recipient);
        root.put("replyto", recipient);
        root.put("message", renderText(event));
        return objectMapper.writeValueAsString(root);
    }

    private String renderText(DocumentLifecycleEvent event) {
        return "Documento " + event.documentId() + " atualizado para " + event.type()
            + ". Evento: " + event.eventId() + ".";
    }

    private void classifyResponse(int status, String body) {
        if ((status >= 200 && status < 300) || status == 303) {
            if (status == 200 && body != null && !body.isBlank()) {
                try {
                    JsonNode response = objectMapper.readTree(body);
                    if (response.has("success") && !response.path("success").asBoolean()) {
                        throw new EmailDeliveryException(
                            EmailDeliveryException.FailureType.PERMANENT,
                            "Web3Forms rejected the e-mail"
                        );
                    }
                } catch (EmailDeliveryException exception) {
                    throw exception;
                } catch (IOException ignored) {
                    // A successful HTTP response remains successful if the provider body is not JSON.
                }
            }
            return;
        }
        EmailDeliveryException.FailureType type = status == 429 || status >= 500
            ? EmailDeliveryException.FailureType.TRANSIENT
            : EmailDeliveryException.FailureType.PERMANENT;
        throw new EmailDeliveryException(type, "Web3Forms rejected the e-mail with HTTP " + status);
    }
}
