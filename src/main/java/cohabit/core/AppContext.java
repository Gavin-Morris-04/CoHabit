package cohabit.core;

import cohabit.model.Room;
import cohabit.model.User;
import java.util.ArrayList;
import java.util.List;

public class AppContext {
    private Room activeRoom;
    private List<User> activeMembers = new ArrayList<>();
    private User activeUser;

    public Room getActiveRoom() {
        return activeRoom;
    }

    public void setActiveRoom(Room activeRoom) {
        this.activeRoom = activeRoom;
    }

    public List<User> getActiveMembers() {
        return activeMembers;
    }

    public void setActiveMembers(List<User> activeMembers) {
        this.activeMembers = activeMembers;
    }

    public User getActiveUser() {
        return activeUser;
    }

    public void setActiveUser(User activeUser) {
        this.activeUser = activeUser;
    }
}
