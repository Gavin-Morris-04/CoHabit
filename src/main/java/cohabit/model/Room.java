package cohabit.model;

import java.util.ArrayList;
import java.util.List;

public class Room {
    private String roomID;
    private String roomUsername;
    private String roomPassword;
    private String name;
    private List<String> memberIds = new ArrayList<>();

    public Room() {
    }

    public Room(String roomID, String name, List<String> memberIds) {
        this.roomID = roomID;
        this.roomUsername = "";
        this.roomPassword = "";
        this.name = name;
        this.memberIds = new ArrayList<>(memberIds);
    }

    public Room(String roomID, String roomUsername, String name, List<String> memberIds) {
        this.roomID = roomID;
        this.roomUsername = roomUsername;
        this.roomPassword = "";
        this.name = name;
        this.memberIds = new ArrayList<>(memberIds);
    }

    public Room(String roomID, String roomUsername, String roomPassword, String name, List<String> memberIds) {
        this.roomID = roomID;
        this.roomUsername = roomUsername;
        this.roomPassword = roomPassword;
        this.name = name;
        this.memberIds = new ArrayList<>(memberIds);
    }

    public String getRoomID() {
        return roomID;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoomUsername() {
        return roomUsername;
    }

    public void setRoomUsername(String roomUsername) {
        this.roomUsername = roomUsername;
    }

    public String getRoomPassword() {
        return roomPassword;
    }

    public void setRoomPassword(String roomPassword) {
        this.roomPassword = roomPassword;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = new ArrayList<>(memberIds);
    }
}
