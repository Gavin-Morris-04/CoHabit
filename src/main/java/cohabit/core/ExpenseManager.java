package cohabit.core;

import cohabit.firebase.FirebaseService;
import cohabit.model.Expense;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExpenseManager {
    private final FirebaseService firebaseService;

    public ExpenseManager(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    public Expense addExpense(
            String roomId,
            String description,
            double amount,
            String paidByUserId,
            boolean paid,
            List<String> memberIds,
            boolean evenSplit,
            Map<String, Double> customPercentages
    ) throws IOException, InterruptedException {
        return addExpense(roomId, description, amount, paidByUserId, paid, memberIds, evenSplit, customPercentages, null);
    }

    public Expense addExpense(
            String roomId,
            String description,
            double amount,
            String paidByUserId,
            boolean paid,
            List<String> memberIds,
            boolean evenSplit,
            Map<String, Double> customPercentages,
            Map<String, Double> payerContributionPercentages
    ) throws IOException, InterruptedException {
        validateInputs(roomId, description, amount, paidByUserId, memberIds, paid);

        Map<String, Double> percentages = evenSplit
                ? buildEvenSplit(memberIds)
                : buildCustomSplit(memberIds, customPercentages, paidByUserId);

        Expense expense = new Expense(
                UUID.randomUUID().toString(),
                roomId,
                description.trim(),
                paidByUserId,
                amount,
                percentages,
                Instant.now()
        );
        expense.setPaid(paid);
        if (paid) {
            Map<String, Double> payers = normalizePayerContributions(memberIds, paidByUserId, payerContributionPercentages);
            expense.setPayerContributionPercentages(payers);
            expense.setPaidAt(Instant.now());
            if (payers.size() > 1) {
                expense.setPaidByUserID("multiple");
            }
        }
        return firebaseService.saveExpense(expense);
    }

    public Expense addRecurringExpense(
            String roomId,
            String description,
            double amount,
            String paidByUserId,
            boolean paid,
            List<String> memberIds,
            boolean evenSplit,
            Map<String, Double> customPercentages
    ) throws IOException, InterruptedException {
        return addRecurringExpense(roomId, description, amount, paidByUserId, paid, memberIds, evenSplit, customPercentages, null);
    }

    public Expense addRecurringExpense(
            String roomId,
            String description,
            double amount,
            String paidByUserId,
            boolean paid,
            List<String> memberIds,
            boolean evenSplit,
            Map<String, Double> customPercentages,
            Map<String, Double> payerContributionPercentages
    ) throws IOException, InterruptedException {
        validateInputs(roomId, description, amount, paidByUserId, memberIds, paid);
        Map<String, Double> percentages = evenSplit
                ? buildEvenSplit(memberIds)
                : (paid ? buildCustomSplit(memberIds, customPercentages, paidByUserId) : buildCustomSplitForRecurring(memberIds, customPercentages));
        Expense recurring = new Expense(
                UUID.randomUUID().toString(),
                roomId,
                description.trim(),
                paidByUserId == null ? "" : paidByUserId,
                amount,
                percentages,
                Instant.now()
        );
        recurring.setRecurring(true);
        recurring.setPaid(paid);
        recurring.setPaidForCurrentCycle(paid);
        if (paid) {
            Map<String, Double> payers = normalizePayerContributions(memberIds, paidByUserId, payerContributionPercentages);
            recurring.setPayerContributionPercentages(payers);
            recurring.setPaidAt(Instant.now());
            if (payers.size() > 1) {
                recurring.setPaidByUserID("multiple");
            }
        }
        recurring.setNextDueAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return firebaseService.saveExpense(recurring);
    }

    public Expense markExpensePaid(
            Expense expense,
            Map<String, Double> payerContributionPercentages,
            boolean evenSplit,
            Map<String, Double> customPercentages,
            List<String> memberIds
    ) throws IOException, InterruptedException {
        if (expense == null) {
            throw new IllegalArgumentException("Expense is required.");
        }
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("Members are required.");
        }
        Map<String, Double> percentages = evenSplit
                ? buildEvenSplit(memberIds)
                : buildCustomSplit(memberIds, customPercentages, "");
        Map<String, Double> payers = normalizePayerContributions(memberIds, "", payerContributionPercentages);
        expense.setPaid(true);
        expense.setPaidByUserID(payers.size() > 1 ? "multiple" : payers.keySet().iterator().next());
        expense.setCustomSplitPercentages(percentages);
        expense.setPayerContributionPercentages(payers);
        expense.setPaidAt(Instant.now());
        return firebaseService.saveExpense(expense);
    }

    public Expense markRecurringExpensePaid(Expense recurringExpense, String paidByUserId) throws IOException, InterruptedException {
        recurringExpense.setPaidByUserID(paidByUserId);
        recurringExpense.setPaid(true);
        recurringExpense.setPaidForCurrentCycle(true);
        recurringExpense.setPayerContributionPercentages(normalizePayerContributions(List.of(paidByUserId), paidByUserId, null));
        recurringExpense.setPaidAt(Instant.now());
        Instant nextDue = recurringExpense.getNextDueAt() == null
                ? Instant.now().plus(30, ChronoUnit.DAYS)
                : recurringExpense.getNextDueAt().plus(30, ChronoUnit.DAYS);
        recurringExpense.setNextDueAt(nextDue);
        return firebaseService.saveExpense(recurringExpense);
    }

    public void deleteExpense(Expense expense) throws IOException, InterruptedException {
        if (expense == null || expense.getExpenseID() == null || expense.getExpenseID().isBlank()) {
            throw new IllegalArgumentException("Valid expense is required.");
        }
        firebaseService.deleteExpense(expense.getExpenseID());
    }

    public List<Expense> getRoomExpenses(String roomId) throws IOException, InterruptedException {
        return firebaseService.getExpensesByRoom(roomId);
    }

    public Map<String, Double> calculateNetBalances(List<Expense> expenses, List<String> memberIds) {
        Map<String, Double> balances = new HashMap<>();
        for (String memberId : memberIds) {
            balances.put(memberId, 0.0);
        }

        for (Expense expense : expenses) {
            if (!expense.isPaid()) {
                continue;
            }
            double amount = expense.getAmount();
            Map<String, Double> payerContributions = expense.getPayerContributionPercentages();
            if (payerContributions != null && !payerContributions.isEmpty()) {
                for (Map.Entry<String, Double> payer : payerContributions.entrySet()) {
                    double paidAmount = amount * (payer.getValue() / 100.0);
                    balances.put(payer.getKey(), balances.getOrDefault(payer.getKey(), 0.0) + paidAmount);
                }
            } else {
                balances.put(expense.getPaidByUserID(), balances.getOrDefault(expense.getPaidByUserID(), 0.0) + amount);
            }
            for (Map.Entry<String, Double> split : expense.getCustomSplitPercentages().entrySet()) {
                double share = amount * (split.getValue() / 100.0);
                balances.put(split.getKey(), balances.getOrDefault(split.getKey(), 0.0) - share);
            }
        }
        return balances;
    }

    private Map<String, Double> buildEvenSplit(List<String> memberIds) {
        double each = 100.0 / memberIds.size();
        Map<String, Double> percentages = new HashMap<>();
        for (String memberId : memberIds) {
            percentages.put(memberId, each);
        }
        return percentages;
    }

    private Map<String, Double> buildCustomSplit(List<String> memberIds, Map<String, Double> customPercentages, String paidByUserId) {
        if (customPercentages == null || customPercentages.isEmpty()) {
            throw new IllegalArgumentException("Custom percentages are required.");
        }
        for (String member : memberIds) {
            if (!customPercentages.containsKey(member)) {
                throw new IllegalArgumentException("Custom split must include all members.");
            }
        }
        double total = customPercentages.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 100.01) {
            throw new IllegalArgumentException("Custom split percentages must total 100.");
        }
        Map<String, Double> result = new HashMap<>(customPercentages);
        if (total < 99.99) {
            if (paidByUserId == null || paidByUserId.isBlank()) {
                throw new IllegalArgumentException("Unpaid custom split must total 100.");
            }
            double remainder = 100.0 - total;
            result.put(paidByUserId, result.getOrDefault(paidByUserId, 0.0) + remainder);
        }
        return result;
    }

    private Map<String, Double> buildCustomSplitForRecurring(List<String> memberIds, Map<String, Double> customPercentages) {
        if (customPercentages == null || customPercentages.isEmpty()) {
            throw new IllegalArgumentException("Custom percentages are required.");
        }
        for (String member : memberIds) {
            if (!customPercentages.containsKey(member)) {
                throw new IllegalArgumentException("Custom split must include all members.");
            }
        }
        double total = customPercentages.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0001) {
            throw new IllegalArgumentException("Custom split percentages cannot all be zero.");
        }
        Map<String, Double> normalized = new HashMap<>();
        for (String memberId : memberIds) {
            normalized.put(memberId, (customPercentages.getOrDefault(memberId, 0.0) / total) * 100.0);
        }
        return normalized;
    }

    private void validateInputs(String roomId, String description, double amount, String paidByUserId, List<String> memberIds, boolean paid) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room is required.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
        if (paid && (paidByUserId == null || paidByUserId.isBlank())) {
            throw new IllegalArgumentException("Paid by user is required.");
        }
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("Members are required.");
        }
    }

    private Map<String, Double> normalizePayerContributions(
            List<String> memberIds,
            String paidByUserId,
            Map<String, Double> payerContributionPercentages
    ) {
        if (payerContributionPercentages == null || payerContributionPercentages.isEmpty()) {
            if (paidByUserId == null || paidByUserId.isBlank()) {
                throw new IllegalArgumentException("Paid by user is required.");
            }
            return Map.of(paidByUserId, 100.0);
        }
        Map<String, Double> normalized = new HashMap<>();
        for (String memberId : memberIds) {
            double pct = payerContributionPercentages.getOrDefault(memberId, 0.0);
            if (pct > 0.0) {
                normalized.put(memberId, pct);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one payer must have a positive contribution.");
        }
        double total = normalized.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 100.0) > 0.05) {
            throw new IllegalArgumentException("Payer contributions must total 100%.");
        }
        return normalized;
    }
}
