package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Chore;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChoreManager {
    private final FirebaseService firebaseService;

    public ChoreManager(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    public Chore addChore(String roomId, String choreName, String assignedUserId) throws IOException, InterruptedException {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room is required.");
        }
        if (choreName == null || choreName.isBlank()) {
            throw new IllegalArgumentException("Chore name is required.");
        }
        if (assignedUserId == null || assignedUserId.isBlank()) {
            throw new IllegalArgumentException("Assigned user is required.");
        }
        Chore chore = new Chore(
                UUID.randomUUID().toString(),
                roomId,
                choreName.trim(),
                assignedUserId,
                "pending",
                Instant.now()
        );
        return firebaseService.saveChore(chore);
    }

    public Chore markCompleted(Chore chore) throws IOException, InterruptedException {
        chore.setStatus("completed");
        chore.setCompletedAt(Instant.now());
        return firebaseService.saveChore(chore);
    }

    public List<Chore> getRoomChores(String roomId) throws IOException, InterruptedException {
        return firebaseService.getChoresByRoom(roomId);
    }

    public void deleteChore(Chore chore) throws IOException, InterruptedException {
        if (chore == null || chore.getChoreID() == null || chore.getChoreID().isBlank()) {
            throw new IllegalArgumentException("Valid chore is required.");
        }
        firebaseService.deleteChore(chore.getChoreID());
    }
}
