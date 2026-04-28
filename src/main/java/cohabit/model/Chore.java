package cohabit.model;

import java.time.Instant;

public class Chore {
    private String choreID;
    private String roomID;
    private String name;
    private String assignedToUserID;
    private String status;
    private Instant createdAt;
    private Instant completedAt;

    public Chore() {
    }

    public Chore(String choreID, String roomID, String name, String assignedToUserID, String status, Instant createdAt) {
        this.choreID = choreID;
        this.roomID = roomID;
        this.name = name;
        this.assignedToUserID = assignedToUserID;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getChoreID() {
        return choreID;
    }

    public void setChoreID(String choreID) {
        this.choreID = choreID;
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

    public String getAssignedToUserID() {
        return assignedToUserID;
    }

    public void setAssignedToUserID(String assignedToUserID) {
        this.assignedToUserID = assignedToUserID;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
