package io.github.raktimchandra.testharness.api;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A tiny in-JVM HTTP server returning canned responses, so harness-api is tested without the SUT. */
final class StubServer implements AutoCloseable {

    record Canned(int status, String contentType, String body) {}

    private final HttpServer server;
    private final Map<String, Canned> routes = new ConcurrentHashMap<>();

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Canned c = routes.getOrDefault(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
                    new Canned(404, "application/problem+json", "{\"status\":404,\"code\":\"NOT_FOUND\"}"));
            byte[] bytes = c.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", c.contentType());
            exchange.getResponseHeaders().add("X-Stub", "yes");
            exchange.sendResponseHeaders(c.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    StubServer on(String methodAndPath, int status, String body) {
        routes.put(methodAndPath, new Canned(status, "application/json", body));
        return this;
    }

    String baseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
