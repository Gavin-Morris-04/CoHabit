package cohabit.firebase;

import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class LocalJsonStore implements FirestoreGateway {
    private final Path filePath;
    private final ObjectMapper mapper;

    public LocalJsonStore(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Room saveRoom(Room room) throws IOException {
        LocalData data = load();
        data.rooms.put(room.getRoomID(), room);
        save(data);
        return room;
    }

    @Override
    public Optional<Room> getRoom(String roomId) throws IOException {
        LocalData data = load();
        return Optional.ofNullable(data.rooms.get(roomId));
    }

    @Override
    public User saveUser(User user) throws IOException {
        LocalData data = load();
        data.users.put(user.getUserID(), user);
        save(data);
        return user;
    }

    @Override
    public List<User> getUsersByIds(List<String> userIds) throws IOException {
        LocalData data = load();
        return userIds.stream()
                .map(data.users::get)
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<User> getAllUsers() throws IOException {
        LocalData data = load();
        return new ArrayList<>(data.users.values());
    }

    @Override
    public Chore saveChore(Chore chore) throws IOException {
        LocalData data = load();
        data.chores.put(chore.getChoreID(), chore);
        save(data);
        return chore;
    }

    @Override
    public List<Chore> getChoresByRoom(String roomId) throws IOException {
        LocalData data = load();
        return data.chores.values().stream()
                .filter(c -> roomId.equals(c.getRoomID()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteChore(String choreId) throws IOException {
        LocalData data = load();
        data.chores.remove(choreId);
        save(data);
    }

    @Override
    public Expense saveExpense(Expense expense) throws IOException {
        LocalData data = load();
        data.expenses.put(expense.getExpenseID(), expense);
        save(data);
        return expense;
    }

    @Override
    public List<Expense> getExpensesByRoom(String roomId) throws IOException {
        LocalData data = load();
        return data.expenses.values().stream()
                .filter(e -> roomId.equals(e.getRoomID()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteExpense(String expenseId) throws IOException {
        LocalData data = load();
        data.expenses.remove(expenseId);
        save(data);
    }

    @Override
    public List<Room> getAllRooms() throws IOException {
        LocalData data = load();
        return new ArrayList<>(data.rooms.values());
    }

    private LocalData load() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            LocalData initial = new LocalData();
            save(initial);
            return initial;
        }
        return mapper.readValue(filePath.toFile(), LocalData.class);
    }

    private void save(LocalData data) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
    }

    private static class LocalData {
        public Map<String, Room> rooms = new HashMap<>();
        public Map<String, User> users = new HashMap<>();
        public Map<String, Chore> chores = new HashMap<>();
        public Map<String, Expense> expenses = new HashMap<>();
    }
}
