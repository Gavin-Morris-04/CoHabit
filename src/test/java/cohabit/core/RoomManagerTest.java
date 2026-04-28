package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Room;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RoomManagerTest {
    @Test
    void createRoomValidatesMemberCountAndStoresUsers() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        RoomManager roomManager = new RoomManager(service);

        Room room = roomManager.createRoom("Apartment 4B", List.of("Alex", "Blair"));
        Assertions.assertNotNull(room.getRoomID());
        Assertions.assertEquals(2, room.getMemberIds().size());
    }

    @Test
    void createRoomRejectsInvalidCounts() {
        InMemoryGateway gateway = new InMemoryGateway();
        FirebaseService service = new FirebaseService(gateway, gateway);
        RoomManager roomManager = new RoomManager(service);

        Assertions.assertThrows(IllegalArgumentException.class, () -> roomManager.createRoom("A", List.of("One")));
    }
}
