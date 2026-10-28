package com.fitnesstracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

public class FitnessTrackerApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
        var allowedOrigins = java.util.Arrays.stream(System.getenv()
                        .getOrDefault("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var json = new ObjectMapper();
        var database = new Database();
        var tokens = new TokenService(System.getenv("JWT_SECRET"), json);
        database.initialize();

        var server = HttpServer.create(new java.net.InetSocketAddress(host, port), 0);
        server.createContext("/", new ApiHandler(json, new FitnessService(database, tokens), tokens, allowedOrigins));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.shutdown();
        }));
        server.start();
        System.out.println("Fitness Tracker API listening on http://" + host + ":" + port);
        System.out.println("Database initialized; seeded 30 built-in exercises");
    }
}
