package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Chore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChoreManagerTest {
    @Test
    void addAndCompleteChoreUpdatesStatusAndTimestamp() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        ChoreManager choreManager = new ChoreManager(service);

        Chore chore = choreManager.addChore("room-1", "Take out trash", "user-1");
        Assertions.assertEquals("pending", chore.getStatus());
        Assertions.assertNull(chore.getCompletedAt());

        Chore completed = choreManager.markCompleted(chore);
        Assertions.assertEquals("completed", completed.getStatus());
        Assertions.assertNotNull(completed.getCompletedAt());
    }
}
