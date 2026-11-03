package com.dockflow.dockflow.document.notification;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local-only HTTP double for Web3Forms responses used by notification tests. */
public final class FakeWeb3FormsServer implements AutoCloseable {

    private static final String SUBMIT_PATH = "/submit";

    private final HttpServer server;
    private final CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
    private volatile int responseStatus;
    private volatile String responseBody;

    private FakeWeb3FormsServer(HttpServer server, int responseStatus, String responseBody) {
        this.server = server;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        server.createContext(SUBMIT_PATH, this::handleSubmitRequest);
        server.start();
    }

    public static FakeWeb3FormsServer start(int responseStatus, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        return new FakeWeb3FormsServer(server, responseStatus, responseBody);
    }

    public URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + SUBMIT_PATH);
    }

    public void respondWith(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    public List<String> requestBodies() {
        return List.copyOf(requestBodies);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleSubmitRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
        }
    }
}
