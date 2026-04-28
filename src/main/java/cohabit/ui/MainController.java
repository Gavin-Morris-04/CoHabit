package cohabit.ui;

import cohabit.core.AppContext;
import cohabit.core.ChoreManager;
import cohabit.core.ExpenseManager;
import cohabit.core.RoomManager;
import cohabit.firebase.FirebaseService;
import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

public class MainController {
    @FXML
    private Label roomNameLabel;
    @FXML
    private Label membersLabel;
    @FXML
    private Label persistenceLabel;
    @FXML
    private Label choreCountLabel;
    @FXML
    private Label completedCountLabel;
    @FXML
    private Label expenseTotalLabel;
    @FXML
    private Label sideActiveUserLabel;
    @FXML
    private Label sideOpenChoresLabel;
    @FXML
    private Label sideUnpaidExpensesLabel;
    @FXML
    private Label sideNextDueLabel;
    @FXML
    private ListView<String> choresSummaryList;
    @FXML
    private ListView<String> choresList;
    @FXML
    private ListView<String> expensesList;
    @FXML
    private ListView<String> balancesSummaryList;
    @FXML
    private PieChart spendingPieChart;
    @FXML
    private ListView<String> spentByPersonList;
    @FXML
    private ComboBox<User> roommateSelectBox;

    private AppContext appContext;
    private RoomManager roomManager;
    private ChoreManager choreManager;
    private ExpenseManager expenseManager;
    private FirebaseService firebaseService;
    private final List<Expense> displayedExpenses = new ArrayList<>();
    private final List<Chore> displayedChores = new ArrayList<>();
    private final List<Expense> paidExpensesForPie = new ArrayList<>();
    private final Map<PieChart.Data, String> pieSliceUserIds = new IdentityHashMap<>();
    private final Map<String, String> knownUserNames = new HashMap<>();
    private final Set<String> selectedExpenseIds = new HashSet<>();
    private final Set<String> selectedChoreIds = new HashSet<>();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd");

    public void init(AppContext appContext, RoomManager roomManager, ChoreManager choreManager, ExpenseManager expenseManager, FirebaseService firebaseService) {
        this.appContext = appContext;
        this.roomManager = roomManager;
        this.choreManager = choreManager;
        this.expenseManager = expenseManager;
        this.firebaseService = firebaseService;
        configureListViews();
        refreshDashboard();
    }

    @FXML
    private void onAddRoommate() {
        try {
            if (appContext.getActiveRoom() == null) {
                showError("No active room loaded.");
                return;
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Add Roommate");
            dialog.setHeaderText("Add a roommate to this room");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");
            ButtonType saveButton = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

            TextField nameField = new TextField();
            nameField.setPromptText("Roommate name");
            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Name"), 0, 0);
            form.add(nameField, 1, 0);
            dialog.getDialogPane().setContent(form);

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != saveButton) {
                return;
            }
            Room updatedRoom = roomManager.addRoommate(appContext.getActiveRoom(), nameField.getText());
            appContext.setActiveRoom(updatedRoom);
            appContext.setActiveMembers(roomManager.getRoomUsers(updatedRoom));
            refreshDashboard();
        } catch (Exception ex) {
            showError("Add roommate failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onRemoveSelectedRoommate() {
        try {
            if (appContext.getActiveRoom() == null) {
                showError("No active room loaded.");
                return;
            }
            User selected = roommateSelectBox.getValue();
            if (selected == null) {
                showError("Select a roommate to remove.");
                return;
            }
            if (appContext.getActiveUser() != null && selected.getUserID().equals(appContext.getActiveUser().getUserID())) {
                showError("You cannot remove the currently active user.");
                return;
            }
            Room updatedRoom = roomManager.removeRoommate(appContext.getActiveRoom(), selected.getUserID());
            appContext.setActiveRoom(updatedRoom);
            appContext.setActiveMembers(roomManager.getRoomUsers(updatedRoom));
            purgeDeletedUserReferences(selected.getUserID(), updatedRoom.getMemberIds());
            refreshDashboard();
        } catch (Exception ex) {
            showError("Remove roommate failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onAddChore() {
        try {
            if (appContext.getActiveMembers().isEmpty()) {
                showError("Add members before creating chores.");
                return;
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Add Chore");
            dialog.setHeaderText("Create and assign a new chore");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");

            ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);

            TextField choreField = new TextField();
            choreField.setPromptText("e.g. Take out recycling");
            ComboBox<User> assigneeBox = new ComboBox<>(FXCollections.observableArrayList(appContext.getActiveMembers()));
            assigneeBox.setPromptText("Assign roommate");
            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Chore name"), 0, 0);
            form.add(choreField, 1, 0);
            form.add(new Label("Assign to"), 0, 1);
            form.add(assigneeBox, 1, 1);
            dialog.getDialogPane().setContent(form);

            ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != createButton) {
                return;
            }
            if (choreField.getText().isBlank() || assigneeBox.getValue() == null) {
                throw new IllegalArgumentException("Chore name and assignee are required.");
            }
            choreManager.addChore(appContext.getActiveRoom().getRoomID(), choreField.getText(), assigneeBox.getValue().getUserID());
            refreshDashboard();
        } catch (Exception ex) {
            showError("Add chore failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onCompleteSelectedChore() {
        List<Chore> selected = getSelectedChores();
        if (selected.isEmpty()) {
            showError("Select at least one chore first.");
            return;
        }
        try {
            for (Chore chosen : selected) {
                choreManager.markCompleted(chosen);
            }
            selectedChoreIds.clear();
            refreshDashboard();
        } catch (Exception ex) {
            showError("Complete chore failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedChore() {
        List<Chore> selected = getSelectedChores();
        if (selected.isEmpty()) {
            showError("Select at least one chore to delete.");
            return;
        }
        try {
            for (Chore chore : selected) {
                choreManager.deleteChore(chore);
            }
            selectedChoreIds.clear();
            refreshDashboard();
        } catch (Exception ex) {
            showError("Delete chore failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onAddExpense() {
        try {
            if (appContext.getActiveMembers().isEmpty()) {
                showError("Add members before creating expenses.");
                return;
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Add Expense");
            dialog.setHeaderText("Track a shared expense");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");

            ButtonType saveButton = new ButtonType("Save Expense", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

            TextField descriptionField = new TextField();
            descriptionField.setPromptText("e.g. Utilities bill");
            TextField amountField = new TextField();
            amountField.setPromptText("0.00");
            CheckBox unpaidBox = new CheckBox("Save as unpaid for now");
            PaymentFormControls paymentControls = buildPaymentControls(0.0);

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Description"), 0, 0);
            form.add(descriptionField, 1, 0);
            form.add(new Label("Amount"), 0, 1);
            form.add(amountField, 1, 1);
            form.add(unpaidBox, 1, 2);
            form.add(new Label("Payers"), 0, 3);
            form.add(paymentControls.container, 1, 3);
            dialog.getDialogPane().setContent(form);

            unpaidBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                paymentControls.container.setDisable(selected);
            });

            ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != saveButton) {
                return;
            }
            if (descriptionField.getText().isBlank() || amountField.getText().isBlank()) {
                throw new IllegalArgumentException("Description and amount are required.");
            }
            boolean paid = !unpaidBox.isSelected();
            double amount = Double.parseDouble(amountField.getText());
            Map<String, Double> payerContributions = paid
                    ? collectPayerPercentages(paymentControls, amount)
                    : Map.of();
            String paidByUserId = payerContributions.isEmpty()
                    ? ""
                    : (payerContributions.size() == 1 ? payerContributions.keySet().iterator().next() : "multiple");

            expenseManager.addExpense(
                    appContext.getActiveRoom().getRoomID(),
                    descriptionField.getText(),
                    amount,
                    paidByUserId,
                    paid,
                    appContext.getActiveRoom().getMemberIds(),
                    true,
                    Map.of(),
                    payerContributions
            );
            refreshDashboard();
        } catch (Exception ex) {
            showError("Add expense failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onAddRecurringExpense() {
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Add Recurring Expense");
            dialog.setHeaderText("Create a monthly expense");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");

            ButtonType saveButton = new ButtonType("Save Recurring", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

            TextField descriptionField = new TextField();
            descriptionField.setPromptText("e.g. Internet bill");
            TextField amountField = new TextField();
            amountField.setPromptText("0.00");
            CheckBox unpaidBox = new CheckBox("Save as unpaid for now");
            unpaidBox.setSelected(true);
            PaymentFormControls paymentControls = buildPaymentControls(0.0);

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Description"), 0, 0);
            form.add(descriptionField, 1, 0);
            form.add(new Label("Amount"), 0, 1);
            form.add(amountField, 1, 1);
            form.add(unpaidBox, 1, 2);
            form.add(new Label("Payers"), 0, 3);
            form.add(paymentControls.container, 1, 3);
            dialog.getDialogPane().setContent(form);

            unpaidBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                paymentControls.container.setDisable(selected);
            });
            paymentControls.container.setDisable(true);

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != saveButton) {
                return;
            }
            if (descriptionField.getText().isBlank() || amountField.getText().isBlank()) {
                throw new IllegalArgumentException("Description and amount are required.");
            }
            boolean paid = !unpaidBox.isSelected();
            double amount = Double.parseDouble(amountField.getText());
            Map<String, Double> payerContributions = paid
                    ? collectPayerPercentages(paymentControls, amount)
                    : Map.of();
            String paidByUserId = payerContributions.isEmpty()
                    ? ""
                    : (payerContributions.size() == 1 ? payerContributions.keySet().iterator().next() : "multiple");

            expenseManager.addRecurringExpense(
                    appContext.getActiveRoom().getRoomID(),
                    descriptionField.getText(),
                    amount,
                    paidByUserId,
                    paid,
                    appContext.getActiveRoom().getMemberIds(),
                    true,
                    Map.of(),
                    payerContributions
            );
            refreshDashboard();
        } catch (Exception ex) {
            showError("Add recurring expense failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedExpense() {
        List<Expense> selected = getSelectedExpenses();
        if (selected.isEmpty()) {
            showError("Select at least one expense to delete.");
            return;
        }
        try {
            for (Expense expense : selected) {
                expenseManager.deleteExpense(expense);
            }
            selectedExpenseIds.clear();
            refreshDashboard();
        } catch (Exception ex) {
            showError("Delete expense failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onMarkSelectedExpensePaid() {
        List<Expense> selectedExpenses = getSelectedExpenses().stream()
                .filter(expense -> !expense.isPaid())
                .collect(Collectors.toList());
        if (selectedExpenses.isEmpty()) {
            showError("Select at least one unpaid expense first.");
            return;
        }
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Mark Expense Paid");
            dialog.setHeaderText("Choose one or multiple payers");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");
            ButtonType saveButton = new ButtonType("Mark Paid", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
            PaymentFormControls paymentControls = buildPaymentControls(selectedExpenses.get(0).getAmount());

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Payers"), 0, 0);
            form.add(paymentControls.container, 1, 0);
            dialog.getDialogPane().setContent(form);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != saveButton) {
                return;
            }
            for (Expense selected : selectedExpenses) {
                Map<String, Double> payerContributions = collectPayerPercentages(paymentControls, selected.getAmount());
                expenseManager.markExpensePaid(selected, payerContributions, true, Map.of(), appContext.getActiveRoom().getMemberIds());
            }
            selectedExpenseIds.clear();
            refreshDashboard();
        } catch (Exception ex) {
            showError("Mark expense paid failed: " + ex.getMessage());
        }
    }

    private void refreshDashboard() {
        if (appContext.getActiveRoom() == null || appContext.getActiveUser() == null) {
            roomNameLabel.setText("No room loaded");
            sideActiveUserLabel.setText("-");
            sideOpenChoresLabel.setText("0");
            sideUnpaidExpensesLabel.setText("0");
            sideNextDueLabel.setText("None");
            return;
        }
        roomNameLabel.setText(appContext.getActiveRoom().getName());
        membersLabel.setText(appContext.getActiveMembers().stream().map(User::getName).collect(Collectors.joining(", ")));
        List<User> removableRoommates = appContext.getActiveMembers().stream()
                .filter(user -> appContext.getActiveUser() == null || !user.getUserID().equals(appContext.getActiveUser().getUserID()))
                .collect(Collectors.toList());
        roommateSelectBox.setItems(FXCollections.observableArrayList(removableRoommates));
        if (roommateSelectBox.getValue() != null) {
            String selectedId = roommateSelectBox.getValue().getUserID();
            roommateSelectBox.setValue(removableRoommates.stream()
                    .filter(user -> user.getUserID().equals(selectedId))
                    .findFirst()
                    .orElse(null));
        }
        persistenceLabel.setText(firebaseService.isUsingFallback() ? "Persistence: Local JSON fallback" : "Persistence: Firebase Firestore");

        try {
            List<Chore> roomChores = choreManager.getRoomChores(appContext.getActiveRoom().getRoomID());
            List<Chore> chores = new ArrayList<>(roomChores);
            cacheKnownUserNames(roomChores, List.of());
            chores.sort((a, b) -> {
                int byStatus = Boolean.compare("completed".equalsIgnoreCase(a.getStatus()), "completed".equalsIgnoreCase(b.getStatus()));
                if (byStatus != 0) {
                    return byStatus;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
            long completed = chores.stream().filter(c -> "completed".equalsIgnoreCase(c.getStatus())).count();
            List<String> formattedChores = chores.stream().map(this::formatChore).collect(Collectors.toList());
            displayedChores.clear();
            displayedChores.addAll(chores);
            selectedChoreIds.retainAll(displayedChores.stream().map(Chore::getChoreID).collect(Collectors.toSet()));
            choresList.setItems(FXCollections.observableArrayList(formattedChores));
            choresSummaryList.setItems(FXCollections.observableArrayList(formattedChores));

            List<Expense> roomExpenses = expenseManager.getRoomExpenses(appContext.getActiveRoom().getRoomID());
            List<Expense> expenses = new ArrayList<>(roomExpenses);
            cacheKnownUserNames(roomChores, roomExpenses);
            expenses.sort((a, b) -> {
                if (a.isRecurring() != b.isRecurring()) {
                    return Boolean.compare(b.isRecurring(), a.isRecurring());
                }
                if (a.isRecurring()) {
                    Instant dueA = a.getNextDueAt() == null ? Instant.MAX : a.getNextDueAt();
                    Instant dueB = b.getNextDueAt() == null ? Instant.MAX : b.getNextDueAt();
                    return dueA.compareTo(dueB);
                }
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });
            double totalExpenseAmount = expenses.stream().filter(Expense::isPaid).mapToDouble(Expense::getAmount).sum();
            displayedExpenses.clear();
            displayedExpenses.addAll(expenses);
            selectedExpenseIds.retainAll(displayedExpenses.stream().map(Expense::getExpenseID).collect(Collectors.toSet()));
            expensesList.setItems(FXCollections.observableArrayList(
                    expenses.stream().map(this::formatExpense).collect(Collectors.toList())
            ));
            List<Expense> recurringExpenses = roomExpenses.stream()
                    .filter(Expense::isRecurring)
                    .sorted((a, b) -> {
                        if (a.isPaidForCurrentCycle() != b.isPaidForCurrentCycle()) {
                            return Boolean.compare(a.isPaidForCurrentCycle(), b.isPaidForCurrentCycle());
                        }
                        Instant dueA = a.getNextDueAt() == null ? Instant.MAX : a.getNextDueAt();
                        Instant dueB = b.getNextDueAt() == null ? Instant.MAX : b.getNextDueAt();
                        return dueA.compareTo(dueB);
                    })
                    .collect(Collectors.toList());
            Map<String, Double> spentByUser = new HashMap<>();
            Set<String> activeMemberIds = new HashSet<>(appContext.getActiveRoom().getMemberIds());
            paidExpensesForPie.clear();
            for (Expense expense : roomExpenses) {
                if (expense.isRecurring() && !expense.isPaidForCurrentCycle()) {
                    continue;
                }
                if (!expense.isPaid()) {
                    continue;
                }
                paidExpensesForPie.add(expense);
                Map<String, Double> payerContributions = resolvePayerContributions(expense);
                for (Map.Entry<String, Double> payer : payerContributions.entrySet()) {
                    if (!activeMemberIds.contains(payer.getKey())) {
                        continue;
                    }
                    double paidAmount = expense.getAmount() * (payer.getValue() / 100.0);
                    if (paidAmount <= 0.005) {
                        continue;
                    }
                    spentByUser.put(
                            payer.getKey(),
                            spentByUser.getOrDefault(payer.getKey(), 0.0) + paidAmount
                    );
                }
            }
            pieSliceUserIds.clear();
            List<PieChart.Data> pieData = new ArrayList<>();
            spentByUser.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        PieChart.Data data = new PieChart.Data(userName(entry.getKey()), entry.getValue());
                        pieData.add(data);
                        pieSliceUserIds.put(data, entry.getKey());
                    });
            List<String> spentByPersonLines = spentByUser.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .map(entry -> userName(entry.getKey()) + ": $" + String.format("%.2f", entry.getValue()))
                    .collect(Collectors.toList());
            balancesSummaryList.setItems(FXCollections.observableArrayList(spentByPersonLines));
            spentByPersonList.setItems(FXCollections.observableArrayList(spentByPersonLines));
            spendingPieChart.setData(FXCollections.observableArrayList(pieData));
            installPieClickHandlers(pieData);

            long unpaidCount = roomExpenses.stream().filter(expense -> !expense.isPaid()).count();
            sideActiveUserLabel.setText(appContext.getActiveUser().getName());
            sideOpenChoresLabel.setText(String.valueOf(chores.stream().filter(c -> !"completed".equalsIgnoreCase(c.getStatus())).count()));
            sideUnpaidExpensesLabel.setText(String.valueOf(unpaidCount));
            String nextDue = recurringExpenses.stream()
                    .filter(expense -> !expense.isPaidForCurrentCycle() && expense.getNextDueAt() != null)
                    .map(Expense::getNextDueAt)
                    .sorted()
                    .findFirst()
                    .map(due -> DATE_FORMAT.format(due.atZone(ZoneId.systemDefault())))
                    .orElse("None");
            sideNextDueLabel.setText(nextDue);

            choreCountLabel.setText(String.valueOf(chores.size()));
            completedCountLabel.setText(String.valueOf(completed));
            expenseTotalLabel.setText(String.format("$%.2f", totalExpenseAmount));
        } catch (IOException | InterruptedException ex) {
            showError("Refresh failed: " + ex.getMessage());
        }
    }

    private String formatChore(Chore chore) {
        String status = "completed".equalsIgnoreCase(chore.getStatus()) ? "Completed" : "Pending";
        return chore.getName() + " | " + userName(chore.getAssignedToUserID()) + " | " + status;
    }

    private String formatExpense(Expense expense) {
        if (expense.isRecurring()) {
            return formatRecurringExpense(expense);
        }
        if (!expense.isPaid()) {
            return expense.getDescription() + " | $" + String.format("%.2f", expense.getAmount()) + " | Unpaid";
        }
        Instant paidAt = expense.getPaidAt() == null ? expense.getCreatedAt() : expense.getPaidAt();
        String paidLabel = "Paid on " + DATE_FORMAT.format(paidAt.atZone(ZoneId.systemDefault()));
        return expense.getDescription() + " | $" + String.format("%.2f", expense.getAmount()) + " | " + paidLabel;
    }

    private String formatRecurringExpense(Expense expense) {
        String due = expense.getNextDueAt() == null
                ? "No due date"
                : DATE_FORMAT.format(expense.getNextDueAt().atZone(ZoneId.systemDefault()));
        String state = expense.isPaidForCurrentCycle()
                ? "Paid on " + DATE_FORMAT.format((expense.getPaidAt() == null ? expense.getCreatedAt() : expense.getPaidAt()).atZone(ZoneId.systemDefault()))
                : "Pending";
        return expense.getDescription()
                + "\nRecurring | $" + String.format("%.2f", expense.getAmount()) + " | due " + due + " | " + state;
    }

    private void configureListViews() {
        choresList.setPlaceholder(buildPlaceholder("No chores yet. Add one to get started."));
        choresSummaryList.setPlaceholder(buildPlaceholder("No chores yet. Add one to get started."));
        expensesList.setPlaceholder(buildPlaceholder("No expenses tracked yet."));
        balancesSummaryList.setPlaceholder(buildPlaceholder("No spending recorded yet."));
        spentByPersonList.setPlaceholder(buildPlaceholder("No spending recorded yet."));

        choresList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        expensesList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        choresList.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.intValue() >= 0) {
                choresList.getSelectionModel().clearSelection();
            }
        });
        expensesList.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.intValue() >= 0) {
                expensesList.getSelectionModel().clearSelection();
            }
        });
        choresList.setCellFactory(list -> new SelectableChoreCell());
        choresSummaryList.setCellFactory(list -> new StatusAwareCell());
        expensesList.setCellFactory(list -> new SelectableExpenseCell());
        balancesSummaryList.setCellFactory(list -> new BalanceCell());

    }

    private String buildPaidStatusText(Expense expense) {
        if (!expense.isPaid()) {
            return "Unpaid";
        }
        Instant paidAt = expense.getPaidAt() == null ? expense.getCreatedAt() : expense.getPaidAt();
        return "Paid on " + DATE_FORMAT.format(paidAt.atZone(ZoneId.systemDefault()));
    }

    private boolean isSinglePersonPaying(Expense expense) {
        long membersWithShare = expense.getCustomSplitPercentages().values().stream()
                .filter(value -> value != null && value > 0.005)
                .count();
        return membersWithShare <= 1;
    }

    private Label buildPlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-placeholder");
        return label;
    }

    private void showExpenseSplitDetails(Expense expense) {
        Map<String, Double> displaySplit = resolvePayerContributions(expense);
        if (displaySplit.isEmpty()) {
            displaySplit = expense.getCustomSplitPercentages();
        }
        String splitDetails = displaySplit.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> {
                    double amount = expense.getAmount() * (entry.getValue() / 100.0);
                    return userName(entry.getKey()) + " -> " + String.format("%.1f%% ($%.2f)", entry.getValue(), amount);
                })
                .collect(Collectors.joining("\n"));
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                "Expense: " + expense.getDescription()
                        + "\nAmount: $" + String.format("%.2f", expense.getAmount())
                        + "\nStatus: " + buildPaidStatusText(expense)
                        + "\nCreated: " + DATE_FORMAT.format(expense.getCreatedAt().atZone(ZoneId.systemDefault()))
                        + "\n\nPaid contribution split:\n" + splitDetails
        );
        alert.setHeaderText("Expense Details");
        alert.showAndWait();
    }

    private void installPieClickHandlers(List<PieChart.Data> pieData) {
        double total = pieData.stream().mapToDouble(PieChart.Data::getPieValue).sum();
        for (PieChart.Data data : pieData) {
            double amount = data.getPieValue();
            double percentage = total <= 0.0 ? 0.0 : (amount / total) * 100.0;
            String userId = pieSliceUserIds.get(data);
            Runnable attach = () -> data.getNode().setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                showPieSliceDetails(data.getName(), userId, amount, percentage);
            });
            if (data.getNode() != null) {
                attach.run();
            } else {
                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null) {
                        attach.run();
                    }
                });
            }
        }
    }

    private void showPieSliceDetails(String name, String userId, double amount, double percentage) {
        List<Expense> personPaidExpenses = paidExpensesForPie.stream()
                .filter(expense -> resolvePayerContributions(expense).containsKey(userId))
                .collect(Collectors.toList());
        List<String> paidOn = personPaidExpenses.stream()
                .map(expense -> {
                    double share = expense.getAmount() * (resolvePayerContributions(expense).getOrDefault(userId, 0.0) / 100.0);
                    return "- " + expense.getDescription() + " ($" + String.format("%.2f", share) + ")";
                })
                .collect(Collectors.toList());
        double totalSpent = personPaidExpenses.stream()
                .mapToDouble(expense -> expense.getAmount() * (resolvePayerContributions(expense).getOrDefault(userId, 0.0) / 100.0))
                .sum();
        String paidOnText = paidOn.isEmpty() ? "- No paid expenses recorded." : String.join("\n", paidOn);
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                name
                        + "\n\nPercentage of total: " + String.format("%.1f%%", percentage)
                        + "\nTotal paid: $" + String.format("%.2f", totalSpent)
                        + "\n\nPaid on:\n" + paidOnText
        );
        alert.setHeaderText("Spending Details");
        alert.showAndWait();
    }

    private Map<String, Double> resolvePayerContributions(Expense expense) {
        if (expense.getPayerContributionPercentages() != null && !expense.getPayerContributionPercentages().isEmpty()) {
            return expense.getPayerContributionPercentages();
        }
        if (expense.getPaidByUserID() == null || expense.getPaidByUserID().isBlank()) {
            return Map.of();
        }
        return Map.of(expense.getPaidByUserID(), 100.0);
    }

    private void purgeDeletedUserReferences(String removedUserId, List<String> remainingMemberIds) throws IOException, InterruptedException {
        if (removedUserId == null || removedUserId.isBlank()) {
            return;
        }
        Set<String> remaining = new HashSet<>(remainingMemberIds);
        String fallbackUserId = appContext.getActiveUser() != null ? appContext.getActiveUser().getUserID() : remainingMemberIds.get(0);

        List<Chore> chores = choreManager.getRoomChores(appContext.getActiveRoom().getRoomID());
        for (Chore chore : chores) {
            if (!removedUserId.equals(chore.getAssignedToUserID())) {
                continue;
            }
            chore.setAssignedToUserID(fallbackUserId);
            firebaseService.saveChore(chore);
        }

        List<Expense> expenses = expenseManager.getRoomExpenses(appContext.getActiveRoom().getRoomID());
        for (Expense expense : expenses) {
            boolean changed = false;

            Map<String, Double> split = new HashMap<>(expense.getCustomSplitPercentages());
            split.entrySet().removeIf(entry -> !remaining.contains(entry.getKey()));
            if (split.isEmpty() && !remainingMemberIds.isEmpty()) {
                double each = 100.0 / remainingMemberIds.size();
                for (String memberId : remainingMemberIds) {
                    split.put(memberId, each);
                }
            } else if (!split.isEmpty()) {
                split = normalizeToHundred(split);
            }
            if (!split.equals(expense.getCustomSplitPercentages())) {
                expense.setCustomSplitPercentages(split);
                changed = true;
            }

            Map<String, Double> payers = new HashMap<>(resolvePayerContributions(expense));
            payers.entrySet().removeIf(entry -> !remaining.contains(entry.getKey()));
            if (expense.isPaid()) {
                if (payers.isEmpty()) {
                    expense.setPaid(false);
                    expense.setPaidByUserID("");
                    expense.setPaidAt(null);
                    expense.setPayerContributionPercentages(Map.of());
                    changed = true;
                } else {
                    Map<String, Double> normalizedPayers = normalizeToHundred(payers);
                    expense.setPayerContributionPercentages(normalizedPayers);
                    expense.setPaidByUserID(
                            normalizedPayers.size() == 1
                                    ? normalizedPayers.keySet().iterator().next()
                                    : "multiple"
                    );
                    changed = true;
                }
            } else if (!expense.getPayerContributionPercentages().isEmpty()) {
                expense.setPayerContributionPercentages(Map.of());
                changed = true;
            }

            if (changed) {
                firebaseService.saveExpense(expense);
            }
        }
    }

    private Map<String, Double> normalizeToHundred(Map<String, Double> raw) {
        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0001) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            normalized.put(entry.getKey(), (entry.getValue() / total) * 100.0);
        }
        return normalized;
    }

    private void cacheKnownUserNames(List<Chore> chores, List<Expense> expenses) throws IOException, InterruptedException {
        Map<String, String> names = appContext.getActiveMembers().stream()
                .collect(Collectors.toMap(User::getUserID, User::getName, (left, right) -> left));
        Set<String> referencedIds = new HashSet<>(names.keySet());

        for (Chore chore : chores) {
            if (chore.getAssignedToUserID() != null && !chore.getAssignedToUserID().isBlank()) {
                referencedIds.add(chore.getAssignedToUserID());
            }
        }
        for (Expense expense : expenses) {
            if (expense.getPaidByUserID() != null && !expense.getPaidByUserID().isBlank() && !"multiple".equals(expense.getPaidByUserID())) {
                referencedIds.add(expense.getPaidByUserID());
            }
            referencedIds.addAll(expense.getCustomSplitPercentages().keySet());
            referencedIds.addAll(resolvePayerContributions(expense).keySet());
        }

        List<String> missingIds = referencedIds.stream()
                .filter(id -> !names.containsKey(id))
                .collect(Collectors.toList());
        if (!missingIds.isEmpty()) {
            List<User> missingUsers = firebaseService.getUsersByIds(missingIds);
            for (User user : missingUsers) {
                names.put(user.getUserID(), user.getName());
            }
        }
        knownUserNames.clear();
        knownUserNames.putAll(names);
    }

    private PaymentFormControls buildPaymentControls(double defaultAmount) {
        ComboBox<User> singlePayerBox = new ComboBox<>(FXCollections.observableArrayList(appContext.getActiveMembers()));
        singlePayerBox.setPromptText("Select payer");
        singlePayerBox.setValue(appContext.getActiveUser());

        ToggleGroup payerModeGroup = new ToggleGroup();
        RadioButton onePayerButton = new RadioButton("One person");
        onePayerButton.setToggleGroup(payerModeGroup);
        onePayerButton.setSelected(true);
        RadioButton multiplePayerButton = new RadioButton("Multiple people");
        multiplePayerButton.setToggleGroup(payerModeGroup);

        ToggleGroup valueModeGroup = new ToggleGroup();
        RadioButton percentModeButton = new RadioButton("Percent");
        percentModeButton.setToggleGroup(valueModeGroup);
        percentModeButton.setSelected(true);
        RadioButton amountModeButton = new RadioButton("Amount");
        amountModeButton.setToggleGroup(valueModeGroup);

        VBox multiPayerFields = new VBox(6);
        Map<String, TextField> payerInputs = new LinkedHashMap<>();
        for (User user : appContext.getActiveMembers()) {
            TextField field = new TextField();
            field.setPromptText(user.getName() + " %");
            payerInputs.put(user.getUserID(), field);
            multiPayerFields.getChildren().add(field);
        }
        multiPayerFields.setDisable(true);

        multiplePayerButton.selectedProperty().addListener((obs, oldVal, selected) -> {
            singlePayerBox.setDisable(selected);
            multiPayerFields.setDisable(!selected);
        });
        amountModeButton.selectedProperty().addListener((obs, oldVal, selected) -> {
            for (User user : appContext.getActiveMembers()) {
                TextField field = payerInputs.get(user.getUserID());
                field.setPromptText(selected ? user.getName() + " $" : user.getName() + " %");
            }
        });

        VBox container = new VBox(8, onePayerButton, singlePayerBox, multiplePayerButton, new VBox(4, percentModeButton, amountModeButton), multiPayerFields);
        return new PaymentFormControls(container, singlePayerBox, onePayerButton, percentModeButton, payerInputs);
    }

    private Map<String, Double> collectPayerPercentages(PaymentFormControls controls, double expenseAmount) {
        if (controls.onePayerButton.isSelected()) {
            User user = controls.singlePayerBox.getValue();
            if (user == null) {
                throw new IllegalArgumentException("Select who paid.");
            }
            return Map.of(user.getUserID(), 100.0);
        }

        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, TextField> entry : controls.payerInputs.entrySet()) {
            String text = entry.getValue().getText();
            boolean blank = text == null || text.isBlank();
            double value = blank ? 0.0 : Double.parseDouble(text);
            if (blank) {
                entry.getValue().setText(controls.percentModeButton.isSelected() ? "0" : "0.00");
            }
            if (value > 0.0) {
                values.put(entry.getKey(), value);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one payer contribution.");
        }

        Map<String, Double> percentages = new LinkedHashMap<>();
        if (controls.percentModeButton.isSelected()) {
            double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(total - 100.0) > 0.05) {
                throw new IllegalArgumentException("Percent contributions must total 100.");
            }
            percentages.putAll(values);
        } else {
            if (expenseAmount <= 0.0) {
                throw new IllegalArgumentException("Amount must be greater than 0.");
            }
            double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(total - expenseAmount) > 0.05) {
                throw new IllegalArgumentException("Amount contributions must total the expense amount.");
            }
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                percentages.put(entry.getKey(), (entry.getValue() / expenseAmount) * 100.0);
            }
        }
        return percentages;
    }

    private List<Chore> getSelectedChores() {
        return displayedChores.stream()
                .filter(chore -> selectedChoreIds.contains(chore.getChoreID()))
                .collect(Collectors.toList());
    }

    private List<Expense> getSelectedExpenses() {
        return displayedExpenses.stream()
                .filter(expense -> selectedExpenseIds.contains(expense.getExpenseID()))
                .collect(Collectors.toList());
    }

    private static class PaymentFormControls {
        private final VBox container;
        private final ComboBox<User> singlePayerBox;
        private final RadioButton onePayerButton;
        private final RadioButton percentModeButton;
        private final Map<String, TextField> payerInputs;

        private PaymentFormControls(
                VBox container,
                ComboBox<User> singlePayerBox,
                RadioButton onePayerButton,
                RadioButton percentModeButton,
                Map<String, TextField> payerInputs
        ) {
            this.container = container;
            this.singlePayerBox = singlePayerBox;
            this.onePayerButton = onePayerButton;
            this.percentModeButton = percentModeButton;
            this.payerInputs = payerInputs;
        }
    }

    private static class StatusAwareCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("chore-pending", "chore-completed");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item);
            if (item.endsWith("Completed")) {
                getStyleClass().add("chore-completed");
            } else {
                getStyleClass().add("chore-pending");
            }
        }
    }

    private class SelectableChoreCell extends ListCell<String> {
        private final CheckBox checkBox = new CheckBox();

        private SelectableChoreCell() {
            checkBox.setOnAction(event -> {
                int idx = getIndex();
                if (idx < 0 || idx >= displayedChores.size()) {
                    return;
                }
                String choreId = displayedChores.get(idx).getChoreID();
                if (checkBox.isSelected()) {
                    selectedChoreIds.add(choreId);
                } else {
                    selectedChoreIds.remove(choreId);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("chore-pending", "chore-completed");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            int idx = getIndex();
            if (idx >= 0 && idx < displayedChores.size()) {
                String choreId = displayedChores.get(idx).getChoreID();
                checkBox.setSelected(selectedChoreIds.contains(choreId));
            }
            checkBox.setText(item);
            setGraphic(checkBox);
            if (item.endsWith("Completed")) {
                getStyleClass().add("chore-completed");
            } else {
                getStyleClass().add("chore-pending");
            }
        }
    }

    private class SelectableExpenseCell extends ListCell<String> {
        private final CheckBox checkBox = new CheckBox();

        private SelectableExpenseCell() {
            checkBox.setOnAction(event -> {
                int idx = getIndex();
                if (idx < 0 || idx >= displayedExpenses.size()) {
                    return;
                }
                String expenseId = displayedExpenses.get(idx).getExpenseID();
                if (checkBox.isSelected()) {
                    selectedExpenseIds.add(expenseId);
                } else {
                    selectedExpenseIds.remove(expenseId);
                }
            });
            setOnMouseClicked(event -> {
                if (event.getClickCount() < 2) {
                    return;
                }
                int idx = getIndex();
                if (idx < 0 || idx >= displayedExpenses.size()) {
                    return;
                }
                showExpenseSplitDetails(displayedExpenses.get(idx));
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            int idx = getIndex();
            if (idx >= 0 && idx < displayedExpenses.size()) {
                String expenseId = displayedExpenses.get(idx).getExpenseID();
                checkBox.setSelected(selectedExpenseIds.contains(expenseId));
            }
            checkBox.setText(item);
            setGraphic(checkBox);
        }
    }

    private static class BalanceCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("balance-positive", "balance-negative", "balance-neutral");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item);
            String[] parts = item.split(":");
            if (parts.length < 2) {
                getStyleClass().add("balance-neutral");
                return;
            }
            try {
                double value = Double.parseDouble(parts[1].trim());
                if (value > 0.005) {
                    getStyleClass().add("balance-positive");
                } else if (value < -0.005) {
                    getStyleClass().add("balance-negative");
                } else {
                    getStyleClass().add("balance-neutral");
                }
            } catch (NumberFormatException ex) {
                getStyleClass().add("balance-neutral");
            }
        }
    }

    private String userName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "-";
        }
        if ("multiple".equals(userId)) {
            return "Multiple";
        }
        return knownUserNames.getOrDefault(userId, userId);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("CoHabit Error");
        alert.showAndWait();
    }
}
