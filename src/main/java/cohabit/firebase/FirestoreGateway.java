package cohabit.firebase;

import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface FirestoreGateway {
    Room saveRoom(Room room) throws IOException, InterruptedException;

    Optional<Room> getRoom(String roomId) throws IOException, InterruptedException;

    User saveUser(User user) throws IOException, InterruptedException;

    List<User> getUsersByIds(List<String> userIds) throws IOException, InterruptedException;

    List<User> getAllUsers() throws IOException, InterruptedException;

    Chore saveChore(Chore chore) throws IOException, InterruptedException;

    List<Chore> getChoresByRoom(String roomId) throws IOException, InterruptedException;

    void deleteChore(String choreId) throws IOException, InterruptedException;

    Expense saveExpense(Expense expense) throws IOException, InterruptedException;

    List<Expense> getExpensesByRoom(String roomId) throws IOException, InterruptedException;

    void deleteExpense(String expenseId) throws IOException, InterruptedException;

    List<Room> getAllRooms() throws IOException, InterruptedException;
}
