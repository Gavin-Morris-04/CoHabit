package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Room;
import cohabit.model.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class RoomManager {
    private final FirebaseService firebaseService;

    public RoomManager(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    public Room createRoom(String roomTitle, List<String> roommateNames) throws IOException, InterruptedException {
        if (roommateNames == null || roommateNames.isEmpty()) {
            throw new IllegalArgumentException("At least one member is required.");
        }
        String ownerName = roommateNames.get(0);
        List<String> others = roommateNames.stream().skip(1).collect(Collectors.toList());
        return createRoom("default-room", roomTitle, ownerName, "default-password", others);
    }

    public Room createRoom(
            String roomUsername,
            String roomTitle,
            String ownerName,
            String roomPassword,
            List<String> roommateNames
    ) throws IOException, InterruptedException {
        validateRoomInputs(roomUsername, roomTitle, ownerName, roomPassword, roommateNames);
        List<User> users = new ArrayList<>();
        List<String> memberIds = new ArrayList<>();
        User owner = new User(UUID.randomUUID().toString(), ownerName.trim(), roomPassword.trim());
        firebaseService.saveUser(owner);
        users.add(owner);
        memberIds.add(owner.getUserID());

        for (String name : roommateNames) {
            User user = new User(UUID.randomUUID().toString(), name.trim(), roomPassword.trim());
            firebaseService.saveUser(user);
            users.add(user);
            memberIds.add(user.getUserID());
        }
        Room room = new Room(UUID.randomUUID().toString(), roomUsername.trim(), roomPassword.trim(), roomTitle.trim(), memberIds);
        return firebaseService.saveRoom(room);
    }

    public Optional<Room> getRoom(String roomId) throws IOException, InterruptedException {
        return firebaseService.getRoom(roomId);
    }

    public List<User> getRoomUsers(Room room) throws IOException, InterruptedException {
        return firebaseService.getUsersByIds(room.getMemberIds());
    }

    public Optional<Room> findRoomByUsernameAndPassword(String roomUsername, String roomPassword) throws IOException, InterruptedException {
        if (roomUsername == null || roomUsername.isBlank() || roomPassword == null || roomPassword.isBlank()) {
            return Optional.empty();
        }
        String normalizedRoomUsername = roomUsername.trim().toLowerCase();
        String normalizedRoomPassword = roomPassword.trim();
        return firebaseService.getAllRooms().stream()
                .filter(room -> room.getRoomUsername() != null && room.getRoomUsername().trim().toLowerCase().equals(normalizedRoomUsername))
                .filter(room -> room.getRoomPassword() != null && room.getRoomPassword().equals(normalizedRoomPassword))
                .findFirst();
    }

    public List<Room> getRoomsForUserIds(List<String> userIds) throws IOException, InterruptedException {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return firebaseService.getAllRooms().stream()
                .filter(room -> room.getMemberIds().stream().anyMatch(userIds::contains))
                .collect(Collectors.toList());
    }

    public Room addRoommate(Room room, String roommateName) throws IOException, InterruptedException {
        if (room == null) {
            throw new IllegalArgumentException("Room is required.");
        }
        if (roommateName == null || roommateName.isBlank()) {
            throw new IllegalArgumentException("Roommate name is required.");
        }
        String normalized = roommateName.trim();
        List<User> users = getRoomUsers(room);
        boolean exists = users.stream().anyMatch(user -> user.getName().equalsIgnoreCase(normalized));
        if (exists) {
            throw new IllegalArgumentException("Roommate already exists.");
        }
        User user = new User(UUID.randomUUID().toString(), normalized, room.getRoomPassword());
        firebaseService.saveUser(user);
        List<String> memberIds = new ArrayList<>(room.getMemberIds());
        memberIds.add(user.getUserID());
        room.setMemberIds(memberIds);
        return firebaseService.saveRoom(room);
    }

    public Room removeRoommate(Room room, String userId) throws IOException, InterruptedException {
        if (room == null) {
            throw new IllegalArgumentException("Room is required.");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Roommate is required.");
        }
        List<String> memberIds = new ArrayList<>(room.getMemberIds());
        if (!memberIds.contains(userId)) {
            throw new IllegalArgumentException("Roommate is not in this room.");
        }
        if (memberIds.size() <= 1) {
            throw new IllegalArgumentException("At least one roommate must remain.");
        }
        memberIds.remove(userId);
        room.setMemberIds(memberIds);
        return firebaseService.saveRoom(room);
    }

    private void validateRoomInputs(String roomUsername, String roomTitle, String ownerName, String roomPassword, List<String> roommateNames) {
        if (roomUsername == null || roomUsername.isBlank()) {
            throw new IllegalArgumentException("Room username is required.");
        }
        if (roomTitle == null || roomTitle.isBlank()) {
            throw new IllegalArgumentException("Room title is required.");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Your name is required.");
        }
        if (roomPassword == null || roomPassword.isBlank()) {
            throw new IllegalArgumentException("Room password is required.");
        }
        if (roommateNames == null || roommateNames.size() < 1 || roommateNames.size() > 9) {
            throw new IllegalArgumentException("Add 1 to 9 roommates.");
        }
        for (String name : roommateNames) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Roommate names cannot be empty.");
            }
            if (name.trim().equalsIgnoreCase(ownerName.trim())) {
                throw new IllegalArgumentException("Do not include your own name in roommates.");
            }
        }
    }
}
