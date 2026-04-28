package cohabit.firebase;

import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirebaseService {
    private final FirestoreGateway remoteGateway;
    private final FirestoreGateway localFallback;
    private boolean usingFallback;

    public FirebaseService(Path fallbackPath) {
        this.localFallback = new LocalJsonStore(fallbackPath);
        Map<String, String> envFileValues = loadDotEnv(Path.of(".env"));
        String projectId = resolveConfig("FIREBASE_PROJECT_ID", envFileValues);
        String apiKey = resolveConfig("FIREBASE_API_KEY", envFileValues);
        if (projectId == null || projectId.isBlank() || apiKey == null || apiKey.isBlank()) {
            this.remoteGateway = null;
            this.usingFallback = true;
        } else {
            this.remoteGateway = new FirestoreRestGateway(projectId, apiKey);
            this.usingFallback = false;
        }
    }

    public FirebaseService(FirestoreGateway remoteGateway, FirestoreGateway localFallback) {
        this.remoteGateway = remoteGateway;
        this.localFallback = localFallback;
        this.usingFallback = remoteGateway == null;
    }

    public boolean isUsingFallback() {
        return usingFallback;
    }

    public Room saveRoom(Room room) throws IOException, InterruptedException {
        return execute(gateway -> gateway.saveRoom(room));
    }

    public Optional<Room> getRoom(String roomId) throws IOException, InterruptedException {
        return execute(gateway -> gateway.getRoom(roomId));
    }

    public User saveUser(User user) throws IOException, InterruptedException {
        return execute(gateway -> gateway.saveUser(user));
    }

    public List<User> getUsersByIds(List<String> userIds) throws IOException, InterruptedException {
        return execute(gateway -> gateway.getUsersByIds(userIds));
    }

    public List<User> getAllUsers() throws IOException, InterruptedException {
        return execute(FirestoreGateway::getAllUsers);
    }

    public Chore saveChore(Chore chore) throws IOException, InterruptedException {
        return execute(gateway -> gateway.saveChore(chore));
    }

    public List<Chore> getChoresByRoom(String roomId) throws IOException, InterruptedException {
        return execute(gateway -> gateway.getChoresByRoom(roomId));
    }

    public void deleteChore(String choreId) throws IOException, InterruptedException {
        execute(gateway -> {
            gateway.deleteChore(choreId);
            return null;
        });
    }

    public Expense saveExpense(Expense expense) throws IOException, InterruptedException {
        return execute(gateway -> gateway.saveExpense(expense));
    }

    public List<Expense> getExpensesByRoom(String roomId) throws IOException, InterruptedException {
        return execute(gateway -> gateway.getExpensesByRoom(roomId));
    }

    public void deleteExpense(String expenseId) throws IOException, InterruptedException {
        execute(gateway -> {
            gateway.deleteExpense(expenseId);
            return null;
        });
    }

    public List<Room> getAllRooms() throws IOException, InterruptedException {
        return execute(FirestoreGateway::getAllRooms);
    }

    private String resolveConfig(String key, Map<String, String> envFileValues) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String fileValue = envFileValues.get(key);
        if (fileValue == null || fileValue.isBlank()) {
            return null;
        }
        return fileValue;
    }

    private Map<String, String> loadDotEnv(Path envPath) {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(envPath)) {
            return values;
        }
        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // If .env cannot be read, default to local fallback behavior.
        }
        return values;
    }

    private <T> T execute(Operation<T> operation) throws IOException, InterruptedException {
        if (remoteGateway == null) {
            usingFallback = true;
            return operation.run(localFallback);
        }

        try {
            T result = operation.run(remoteGateway);
            usingFallback = false;
            return result;
        } catch (IOException | InterruptedException ex) {
            usingFallback = true;
            return operation.run(localFallback);
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run(FirestoreGateway gateway) throws IOException, InterruptedException;
    }
}
