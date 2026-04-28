package cohabit.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Expense {
    private String expenseID;
    private String roomID;
    private String description;
    private String paidByUserID;
    private double amount;
    private Map<String, Double> customSplitPercentages = new HashMap<>();
    private Instant createdAt;
    private boolean paid;
    private boolean recurring;
    private Instant nextDueAt;
    private boolean paidForCurrentCycle;

    public Expense() {
    }

    public Expense(String expenseID, String roomID, String description, String paidByUserID, double amount, Map<String, Double> customSplitPercentages, Instant createdAt) {
        this.expenseID = expenseID;
        this.roomID = roomID;
        this.description = description;
        this.paidByUserID = paidByUserID;
        this.amount = amount;
        this.customSplitPercentages = new HashMap<>(customSplitPercentages);
        this.createdAt = createdAt;
        this.paid = true;
        this.recurring = false;
        this.nextDueAt = null;
        this.paidForCurrentCycle = true;
    }

    public String getExpenseID() {
        return expenseID;
    }

    public void setExpenseID(String expenseID) {
        this.expenseID = expenseID;
    }

    public String getRoomID() {
        return roomID;
    }

    public void setRoomID(String roomID) {
        this.roomID = roomID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPaidByUserID() {
        return paidByUserID;
    }

    public void setPaidByUserID(String paidByUserID) {
        this.paidByUserID = paidByUserID;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Map<String, Double> getCustomSplitPercentages() {
        return customSplitPercentages;
    }

    public void setCustomSplitPercentages(Map<String, Double> customSplitPercentages) {
        this.customSplitPercentages = new HashMap<>(customSplitPercentages);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public Instant getNextDueAt() {
        return nextDueAt;
    }

    public void setNextDueAt(Instant nextDueAt) {
        this.nextDueAt = nextDueAt;
    }

    public boolean isPaidForCurrentCycle() {
        return paidForCurrentCycle;
    }

    public void setPaidForCurrentCycle(boolean paidForCurrentCycle) {
        this.paidForCurrentCycle = paidForCurrentCycle;
    }
}
