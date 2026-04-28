package cohabit.core;

import cohabit.firebase.FirestoreGateway;
import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InMemoryGateway implements FirestoreGateway {
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Chore> chores = new HashMap<>();
    private final Map<String, Expense> expenses = new HashMap<>();

    @Override
    public Room saveRoom(Room room) {
        rooms.put(room.getRoomID(), room);
        return room;
    }

    @Override
    public Optional<Room> getRoom(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public User saveUser(User user) {
        users.put(user.getUserID(), user);
        return user;
    }

    @Override
    public List<User> getUsersByIds(List<String> userIds) {
        List<User> result = new ArrayList<>();
        for (String id : userIds) {
            if (users.containsKey(id)) {
                result.add(users.get(id));
            }
        }
        return result;
    }

    @Override
    public Chore saveChore(Chore chore) {
        chores.put(chore.getChoreID(), chore);
        return chore;
    }

    @Override
    public List<Chore> getChoresByRoom(String roomId) {
        return chores.values().stream().filter(c -> roomId.equals(c.getRoomID())).collect(Collectors.toList());
    }

    @Override
    public Expense saveExpense(Expense expense) {
        expenses.put(expense.getExpenseID(), expense);
        return expense;
    }

    @Override
    public List<Expense> getExpensesByRoom(String roomId) {
        return expenses.values().stream().filter(e -> roomId.equals(e.getRoomID())).collect(Collectors.toList());
    }
}
