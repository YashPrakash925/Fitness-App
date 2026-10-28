package com.fitnesstracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApiHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 1_000_000;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private final ObjectMapper json;
    private final FitnessService service;
    private final TokenService tokens;
    private final Set<String> allowedOrigins;

    public ApiHandler(ObjectMapper json, FitnessService service, TokenService tokens, Set<String> allowedOrigins) {
        this.json = json;
        this.service = service;
        this.tokens = tokens;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod().toUpperCase(java.util.Locale.ROOT);
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, Map.of("name", "Fitness Tracker API", "frontend", "http://localhost:5173"));
                return;
            }
            if ("/api/health".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, Map.of("status", "ok"));
                return;
            }
            if ("/api/auth/register".equals(path) && "POST".equals(method)) {
                var body = readObject(exchange);
                sendJson(exchange, 201, service.register(string(body, "displayName"), string(body, "email"), string(body, "password")));
                return;
            }
            if ("/api/auth/login".equals(path) && "POST".equals(method)) {
                var body = readObject(exchange);
                sendJson(exchange, 200, service.login(string(body, "email"), string(body, "password")));
                return;
            }

            long userId = tokens.requireUserId(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("/api/me".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, service.profile(userId));
                return;
            }
            if ("/api/exercises".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, service.listExercises(userId));
                return;
            }
            if ("/api/exercises".equals(path) && "POST".equals(method)) {
                var body = readObject(exchange);
                sendJson(exchange, 201, service.createExercise(userId, string(body, "name"),
                        string(body, "muscleGroup"), string(body, "equipment")));
                return;
            }
            if ("/api/workouts".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, service.workoutHistory(userId));
                return;
            }
            if ("/api/workouts".equals(path) && "POST".equals(method)) {
                sendJson(exchange, 201, service.startWorkout(userId));
                return;
            }

            String[] parts = path.split("/");
            if (parts.length == 5 && "api".equals(parts[1]) && "workouts".equals(parts[2])) {
                long workoutId = positiveId(parts[3]);
                if ("sets".equals(parts[4]) && "POST".equals(method)) {
                    var body = readObject(exchange);
                    sendJson(exchange, 200, service.addSet(userId, workoutId, number(body, "exerciseId").longValue(),
                            number(body, "reps").intValue(), number(body, "weightKg").doubleValue()));
                    return;
                }
                if ("finish".equals(parts[4]) && "POST".equals(method)) {
                    sendJson(exchange, 200, service.finishWorkout(userId, workoutId));
                    return;
                }
            }
            sendJson(exchange, 404, Map.of("message", "API route not found"));
        } catch (ApiException ex) {
            sendJson(exchange, ex.status(), Map.of("message", ex.getMessage()));
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            sendJson(exchange, 400, Map.of("message", "Request body is invalid"));
        } catch (SQLException ex) {
            System.err.println("Database request failed: " + ex.getMessage());
            sendJson(exchange, 500, Map.of("message", "Database request failed"));
        } catch (Exception ex) {
            System.err.println("API request failed: " + ex.getMessage());
            sendJson(exchange, 500, Map.of("message", "Server error"));
        } finally {
            exchange.close();
        }
    }

    private Map<String, Object> readObject(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new ApiException(413, "Request body is too large");
        if (bytes.length == 0) throw new ApiException(400, "Request body is required");
        return json.readValue(bytes, JSON_OBJECT);
    }

    private String string(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text)) throw new ApiException(400, "Missing or invalid field: " + field);
        return text;
    }

    private Number number(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof Number number)) throw new ApiException(400, "Missing or invalid field: " + field);
        return number;
    }

    private long positiveId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ApiException(404, "Workout not found");
        }
    }

    private void setCorsHeaders(HttpExchange exchange) {
        var origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }
}
