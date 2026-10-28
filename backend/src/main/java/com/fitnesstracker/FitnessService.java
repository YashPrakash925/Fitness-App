package com.fitnesstracker;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FitnessService {
    private final Database database;
    private final PasswordHasher passwords = new PasswordHasher();
    private final TokenService tokens;

    public FitnessService(Database database, TokenService tokens) {
        this.database = database;
        this.tokens = tokens;
    }

    public Map<String, Object> register(String displayName, String rawEmail, String password) throws SQLException {
        String name = displayName == null ? "" : displayName.trim();
        String email = normalizeEmail(rawEmail);
        if (name.isBlank() || name.length() > 80) throw new ApiException(400, "Enter a name with at most 80 characters");
        validatePassword(password);
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "INSERT INTO users(email, display_name, password_hash) VALUES (?, ?, ?) RETURNING id, email, display_name")) {
            statement.setString(1, email);
            statement.setString(2, name);
            statement.setString(3, passwords.hash(password));
            try (var result = statement.executeQuery()) {
                result.next();
                return authResponse(result.getLong("id"), result.getString("email"), result.getString("display_name"));
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw new ApiException(409, "An account with that email already exists");
            throw ex;
        }
    }

    public Map<String, Object> login(String rawEmail, String password) throws SQLException {
        String email = normalizeEmail(rawEmail);
        if (password == null || password.isEmpty()) throw new ApiException(400, "Enter your password");
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "SELECT id, email, display_name, password_hash FROM users WHERE email = ?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !passwords.verify(password, result.getString("password_hash"))) {
                    throw new ApiException(401, "Email or password is incorrect");
                }
                return authResponse(result.getLong("id"), result.getString("email"), result.getString("display_name"));
            }
        }
    }

    public Map<String, Object> profile(long userId) throws SQLException {
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "SELECT id, email, display_name FROM users WHERE id = ?")) {
            statement.setLong(1, userId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new ApiException(401, "Account not found");
                return user(result.getLong("id"), result.getString("email"), result.getString("display_name"));
            }
        }
    }

    public List<Map<String, Object>> listExercises(long userId) throws SQLException {
        String sql = """
                SELECT id, name, muscle_group, equipment, owner_id
                FROM exercises WHERE owner_id IS NULL OR owner_id = ?
                ORDER BY name
                """;
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (var result = statement.executeQuery()) {
                var exercises = new ArrayList<Map<String, Object>>();
                while (result.next()) exercises.add(exercise(result));
                return exercises;
            }
        }
    }

    public Map<String, Object> createExercise(long userId, String name, String muscleGroup, String equipment) throws SQLException {
        name = name == null ? "" : name.trim();
        muscleGroup = muscleGroup == null ? "" : muscleGroup.trim();
        equipment = equipment == null ? "" : equipment.trim();
        if (name.isBlank() || name.length() > 100) throw new ApiException(400, "Exercise name must be 1–100 characters");
        if (muscleGroup.isBlank() || muscleGroup.length() > 30) throw new ApiException(400, "Muscle group must be 1–30 characters");
        if (equipment.isBlank() || equipment.length() > 20) throw new ApiException(400, "Equipment must be 1–20 characters");

        String sql = """
                INSERT INTO exercises(name, muscle_group, equipment, owner_id)
                VALUES (?, ?, ?, ?)
                RETURNING id, name, muscle_group, equipment, owner_id
                """;
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, muscleGroup);
            statement.setString(3, equipment);
            statement.setLong(4, userId);
            try (var result = statement.executeQuery()) {
                result.next();
                return exercise(result);
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw new ApiException(409, "You already added an exercise with that name");
            throw ex;
        }
    }

    public Map<String, Object> startWorkout(long userId) throws SQLException {
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "INSERT INTO workouts(user_id) VALUES (?) RETURNING id")) {
            statement.setLong(1, userId);
            try (var result = statement.executeQuery()) {
                result.next();
                return workout(connection, result.getLong("id"), userId);
            }
        }
    }

    public List<Map<String, Object>> workoutHistory(long userId) throws SQLException {
        var ids = new ArrayList<Long>();
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "SELECT id FROM workouts WHERE user_id = ? ORDER BY started_at DESC")) {
            statement.setLong(1, userId);
            try (var result = statement.executeQuery()) {
                while (result.next()) ids.add(result.getLong("id"));
            }
        }
        var history = new ArrayList<Map<String, Object>>();
        for (long id : ids) {
            try (Connection connection = database.connect()) { history.add(workout(connection, id, userId)); }
        }
        return history;
    }

    public Map<String, Object> addSet(long userId, long workoutId, long exerciseId, int reps, double weightKg) throws SQLException {
        if (reps < 1 || reps > 1000) throw new ApiException(400, "Reps must be between 1 and 1000");
        if (!Double.isFinite(weightKg) || weightKg < 0 || weightKg > 100_000) throw new ApiException(400, "Weight must be between 0 and 100000 kg");

        try (Connection connection = database.connect()) {
            Timestamp finishedAt;
            try (var statement = connection.prepareStatement("SELECT finished_at FROM workouts WHERE id = ? AND user_id = ?")) {
                statement.setLong(1, workoutId);
                statement.setLong(2, userId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new ApiException(404, "Workout not found");
                    finishedAt = result.getTimestamp("finished_at");
                }
            }
            if (finishedAt != null) throw new ApiException(409, "This workout is already finished");

            try (var statement = connection.prepareStatement(
                    "SELECT id FROM exercises WHERE id = ? AND (owner_id IS NULL OR owner_id = ?)")) {
                statement.setLong(1, exerciseId);
                statement.setLong(2, userId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) throw new ApiException(404, "Exercise not found");
                }
            }

            try (var statement = connection.prepareStatement(
                    "INSERT INTO workout_sets(workout_id, exercise_id, reps, weight_kg) VALUES (?, ?, ?, ?)")) {
                statement.setLong(1, workoutId);
                statement.setLong(2, exerciseId);
                statement.setInt(3, reps);
                statement.setDouble(4, weightKg);
                statement.executeUpdate();
            }
            return workout(connection, workoutId, userId);
        }
    }

    public Map<String, Object> finishWorkout(long userId, long workoutId) throws SQLException {
        try (Connection connection = database.connect(); var statement = connection.prepareStatement(
                "UPDATE workouts SET finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP) WHERE id = ? AND user_id = ?")) {
            statement.setLong(1, workoutId);
            statement.setLong(2, userId);
            if (statement.executeUpdate() == 0) throw new ApiException(404, "Workout not found");
            return workout(connection, workoutId, userId);
        }
    }

    private Map<String, Object> workout(Connection connection, long workoutId, long userId) throws SQLException {
        Instant startedAt;
        Instant finishedAt;
        try (var statement = connection.prepareStatement(
                "SELECT started_at, finished_at FROM workouts WHERE id = ? AND user_id = ?")) {
            statement.setLong(1, workoutId);
            statement.setLong(2, userId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new ApiException(404, "Workout not found");
                startedAt = result.getTimestamp("started_at").toInstant();
                Timestamp finishTimestamp = result.getTimestamp("finished_at");
                finishedAt = finishTimestamp == null ? null : finishTimestamp.toInstant();
            }
        }

        var setEntries = new ArrayList<Map<String, Object>>();
        int totalReps = 0;
        double totalVolume = 0;
        String setSql = """
                SELECT s.id, s.exercise_id, e.name, e.muscle_group, s.reps, s.weight_kg, s.logged_at
                FROM workout_sets s JOIN exercises e ON e.id = s.exercise_id
                WHERE s.workout_id = ? ORDER BY s.logged_at, s.id
                """;
        try (var statement = connection.prepareStatement(setSql)) {
            statement.setLong(1, workoutId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    int reps = result.getInt("reps");
                    double weightKg = result.getDouble("weight_kg");
                    double volume = reps * weightKg;
                    totalReps += reps;
                    totalVolume += volume;
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("id", result.getLong("id"));
                    entry.put("exerciseId", result.getLong("exercise_id"));
                    entry.put("exerciseName", result.getString("name"));
                    entry.put("muscleGroup", result.getString("muscle_group"));
                    entry.put("reps", reps);
                    entry.put("weightKg", weightKg);
                    entry.put("volumeKg", volume);
                    entry.put("loggedAt", result.getTimestamp("logged_at").toInstant().toString());
                    setEntries.add(entry);
                }
            }
        }

        Instant end = finishedAt == null ? Instant.now() : finishedAt;
        var response = new LinkedHashMap<String, Object>();
        response.put("id", workoutId);
        response.put("startedAt", startedAt.toString());
        response.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
        response.put("durationSeconds", Math.max(0, Duration.between(startedAt, end).getSeconds()));
        response.put("totalVolumeKg", totalVolume);
        response.put("totalReps", totalReps);
        response.put("sets", setEntries);
        return response;
    }

    private Map<String, Object> authResponse(long id, String email, String name) {
        return Map.of("token", tokens.create(id, email), "user", user(id, email, name));
    }

    private Map<String, Object> user(long id, String email, String name) {
        return Map.of("id", id, "email", email, "displayName", name);
    }

    private Map<String, Object> exercise(java.sql.ResultSet result) throws SQLException {
        var response = new LinkedHashMap<String, Object>();
        response.put("id", result.getLong("id"));
        response.put("name", result.getString("name"));
        response.put("muscleGroup", result.getString("muscle_group"));
        response.put("equipment", result.getString("equipment"));
        response.put("custom", result.getObject("owner_id") != null);
        return response;
    }

    private static String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ApiException(400, "Enter a valid email address");
        }
        return email;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ApiException(400, "Password must be between 8 and 128 characters");
        }
    }
}
