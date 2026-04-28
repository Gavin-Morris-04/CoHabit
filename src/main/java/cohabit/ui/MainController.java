package cohabit.ui;

import cohabit.core.AppContext;
import cohabit.core.ChoreManager;
import cohabit.core.ExpenseManager;
import cohabit.firebase.FirebaseService;
import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.User;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    private AppContext appContext;
    private ChoreManager choreManager;
    private ExpenseManager expenseManager;
    private FirebaseService firebaseService;
    private final List<Expense> displayedExpenses = new ArrayList<>();
    private final List<Chore> displayedChores = new ArrayList<>();
    private final List<Expense> paidExpensesForPie = new ArrayList<>();
    private final Map<PieChart.Data, String> pieSliceUserIds = new IdentityHashMap<>();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd");

    public void init(AppContext appContext, ChoreManager choreManager, ExpenseManager expenseManager, FirebaseService firebaseService) {
        this.appContext = appContext;
        this.choreManager = choreManager;
        this.expenseManager = expenseManager;
        this.firebaseService = firebaseService;
        configureListViews();
        refreshDashboard();
    }

    @FXML
    public void onRefresh() {
        refreshDashboard();
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
        int idx = choresList.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            showError("Select a chore first.");
            return;
        }
        try {
            if (idx >= displayedChores.size()) {
                return;
            }
            Chore chosen = displayedChores.get(idx);
            choreManager.markCompleted(chosen);
            refreshDashboard();
        } catch (Exception ex) {
            showError("Complete chore failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedChore() {
        int idx = choresList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= displayedChores.size()) {
            showError("Select a chore to delete.");
            return;
        }
        try {
            choreManager.deleteChore(displayedChores.get(idx));
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
            ComboBox<User> paidByBox = new ComboBox<>(FXCollections.observableArrayList(appContext.getActiveMembers()));
            paidByBox.setPromptText("Select who paid");
            paidByBox.setValue(appContext.getActiveUser());
            CheckBox unpaidBox = new CheckBox("Save as unpaid for now");
            ToggleGroup splitGroup = new ToggleGroup();
            RadioButton evenSplitButton = new RadioButton("Even split");
            evenSplitButton.setToggleGroup(splitGroup);
            evenSplitButton.setSelected(true);
            RadioButton customSplitButton = new RadioButton("Custom split");
            customSplitButton.setToggleGroup(splitGroup);

            VBox customBox = new VBox(8);
            List<TextField> customFields = new ArrayList<>();
            for (User user : appContext.getActiveMembers()) {
                TextField percentField = new TextField();
                percentField.setPromptText(user.getName() + " %");
                customFields.add(percentField);
                customBox.getChildren().add(percentField);
            }
            customBox.setDisable(true);
            customSplitButton.selectedProperty().addListener((obs, oldVal, selected) -> customBox.setDisable(!selected));

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Description"), 0, 0);
            form.add(descriptionField, 1, 0);
            form.add(new Label("Amount"), 0, 1);
            form.add(amountField, 1, 1);
            form.add(new Label("Paid by"), 0, 2);
            form.add(paidByBox, 1, 2);
            form.add(unpaidBox, 1, 3);
            form.add(new Label("Split"), 0, 4);
            VBox splitBox = new VBox(6, evenSplitButton, customSplitButton, customBox);
            form.add(splitBox, 1, 4);
            dialog.getDialogPane().setContent(form);

            unpaidBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                paidByBox.setDisable(selected);
                if (selected) {
                    paidByBox.setValue(null);
                } else if (paidByBox.getValue() == null) {
                    paidByBox.setValue(appContext.getActiveUser());
                }
            });

            ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != saveButton) {
                return;
            }
            if (descriptionField.getText().isBlank() || amountField.getText().isBlank()) {
                throw new IllegalArgumentException("Description and amount are required.");
            }
            boolean paid = !unpaidBox.isSelected();
            if (paid && paidByBox.getValue() == null) {
                throw new IllegalArgumentException("Pick who paid or save it as unpaid.");
            }

            double amount = Double.parseDouble(amountField.getText());
            boolean evenSplit = evenSplitButton.isSelected();
            Map<String, Double> custom = new HashMap<>();
            if (!evenSplit) {
                for (int i = 0; i < appContext.getActiveMembers().size(); i++) {
                    User user = appContext.getActiveMembers().get(i);
                    String input = customFields.get(i).getText();
                    custom.put(user.getUserID(), input.isBlank() ? 0.0 : Double.parseDouble(input));
                }
            }

            expenseManager.addExpense(
                    appContext.getActiveRoom().getRoomID(),
                    descriptionField.getText(),
                    amount,
                    paid ? paidByBox.getValue().getUserID() : "",
                    paid,
                    appContext.getActiveRoom().getMemberIds(),
                    evenSplit,
                    custom
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
            ComboBox<User> paidByBox = new ComboBox<>(FXCollections.observableArrayList(appContext.getActiveMembers()));
            paidByBox.setPromptText("Select who paid");
            paidByBox.setValue(appContext.getActiveUser());
            CheckBox unpaidBox = new CheckBox("Save as unpaid for now");
            unpaidBox.setSelected(true);
            ToggleGroup splitGroup = new ToggleGroup();
            RadioButton evenSplitButton = new RadioButton("Even split");
            evenSplitButton.setToggleGroup(splitGroup);
            evenSplitButton.setSelected(true);
            RadioButton customSplitButton = new RadioButton("Custom split");
            customSplitButton.setToggleGroup(splitGroup);
            VBox customBox = new VBox(8);
            List<TextField> customFields = new ArrayList<>();
            for (User user : appContext.getActiveMembers()) {
                TextField percentField = new TextField();
                percentField.setPromptText(user.getName() + " %");
                customFields.add(percentField);
                customBox.getChildren().add(percentField);
            }
            customBox.setDisable(true);
            customSplitButton.selectedProperty().addListener((obs, oldVal, selected) -> customBox.setDisable(!selected));

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Description"), 0, 0);
            form.add(descriptionField, 1, 0);
            form.add(new Label("Amount"), 0, 1);
            form.add(amountField, 1, 1);
            form.add(new Label("Paid by"), 0, 2);
            form.add(paidByBox, 1, 2);
            form.add(unpaidBox, 1, 3);
            form.add(new Label("Split"), 0, 4);
            form.add(new VBox(6, evenSplitButton, customSplitButton, customBox), 1, 4);
            dialog.getDialogPane().setContent(form);

            unpaidBox.selectedProperty().addListener((obs, oldVal, selected) -> {
                paidByBox.setDisable(selected);
                if (selected) {
                    paidByBox.setValue(null);
                } else if (paidByBox.getValue() == null) {
                    paidByBox.setValue(appContext.getActiveUser());
                }
            });
            paidByBox.setDisable(true);

            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != saveButton) {
                return;
            }
            if (descriptionField.getText().isBlank() || amountField.getText().isBlank()) {
                throw new IllegalArgumentException("Description and amount are required.");
            }
            boolean paid = !unpaidBox.isSelected();
            if (paid && paidByBox.getValue() == null) {
                throw new IllegalArgumentException("Pick who paid or save it as unpaid.");
            }
            double amount = Double.parseDouble(amountField.getText());
            boolean evenSplit = evenSplitButton.isSelected();
            Map<String, Double> custom = new HashMap<>();
            if (!evenSplit) {
                for (int i = 0; i < appContext.getActiveMembers().size(); i++) {
                    String input = customFields.get(i).getText();
                    custom.put(appContext.getActiveMembers().get(i).getUserID(), input.isBlank() ? 0.0 : Double.parseDouble(input));
                }
            }

            expenseManager.addRecurringExpense(
                    appContext.getActiveRoom().getRoomID(),
                    descriptionField.getText(),
                    amount,
                    paid ? paidByBox.getValue().getUserID() : "",
                    paid,
                    appContext.getActiveRoom().getMemberIds(),
                    evenSplit,
                    custom
            );
            refreshDashboard();
        } catch (Exception ex) {
            showError("Add recurring expense failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onMarkRecurringExpensePaid() {
        int idx = expensesList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= displayedExpenses.size()) {
            showError("Select a recurring expense from the expenses list first.");
            return;
        }
        Expense selected = displayedExpenses.get(idx);
        if (!selected.isRecurring()) {
            showError("Selected expense is not recurring.");
            return;
        }
        try {
            expenseManager.markRecurringExpensePaid(selected, appContext.getActiveUser().getUserID());
            refreshDashboard();
        } catch (Exception ex) {
            showError("Mark recurring expense paid failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onDeleteSelectedExpense() {
        int idx = expensesList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= displayedExpenses.size()) {
            showError("Select an expense to delete.");
            return;
        }
        try {
            expenseManager.deleteExpense(displayedExpenses.get(idx));
            refreshDashboard();
        } catch (Exception ex) {
            showError("Delete expense failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onMarkSelectedExpensePaid() {
        int idx = expensesList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= displayedExpenses.size()) {
            showError("Select an expense first.");
            return;
        }
        Expense selected = displayedExpenses.get(idx);
        if (selected.isPaid()) {
            showError("Selected expense is already marked paid.");
            return;
        }
        try {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Mark Expense Paid");
            dialog.setHeaderText("Choose who paid and how it was split");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("cohabit-dialog");
            ButtonType saveButton = new ButtonType("Mark Paid", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
            ComboBox<User> paidByBox = new ComboBox<>(FXCollections.observableArrayList(appContext.getActiveMembers()));
            paidByBox.setPromptText("Select who paid");
            paidByBox.setValue(appContext.getActiveUser());

            ToggleGroup splitGroup = new ToggleGroup();
            RadioButton evenSplitButton = new RadioButton("Even split");
            evenSplitButton.setToggleGroup(splitGroup);
            evenSplitButton.setSelected(true);
            RadioButton customSplitButton = new RadioButton("Custom split");
            customSplitButton.setToggleGroup(splitGroup);
            VBox customBox = new VBox(8);
            List<TextField> customFields = new ArrayList<>();
            for (User user : appContext.getActiveMembers()) {
                TextField percentField = new TextField();
                Double existing = selected.getCustomSplitPercentages().get(user.getUserID());
                if (existing != null && existing > 0.0) {
                    percentField.setText(String.format("%.2f", existing));
                }
                percentField.setPromptText(user.getName() + " %");
                customFields.add(percentField);
                customBox.getChildren().add(percentField);
            }
            customBox.setDisable(true);
            customSplitButton.selectedProperty().addListener((obs, oldVal, isCustom) -> customBox.setDisable(!isCustom));

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Paid by"), 0, 0);
            form.add(paidByBox, 1, 0);
            form.add(new Label("Split"), 0, 1);
            form.add(new VBox(6, evenSplitButton, customSplitButton, customBox), 1, 1);
            dialog.getDialogPane().setContent(form);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != saveButton) {
                return;
            }
            if (paidByBox.getValue() == null) {
                throw new IllegalArgumentException("Paid by user is required.");
            }
            boolean evenSplit = evenSplitButton.isSelected();
            Map<String, Double> custom = new HashMap<>();
            if (!evenSplit) {
                for (int i = 0; i < appContext.getActiveMembers().size(); i++) {
                    String input = customFields.get(i).getText();
                    custom.put(appContext.getActiveMembers().get(i).getUserID(), input.isBlank() ? 0.0 : Double.parseDouble(input));
                }
            }
            expenseManager.markExpensePaid(
                    selected,
                    paidByBox.getValue().getUserID(),
                    evenSplit,
                    custom,
                    appContext.getActiveRoom().getMemberIds()
            );
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
        persistenceLabel.setText(firebaseService.isUsingFallback() ? "Persistence: Local JSON fallback" : "Persistence: Firebase Firestore");

        try {
            List<Chore> roomChores = choreManager.getRoomChores(appContext.getActiveRoom().getRoomID());
            List<Chore> chores = new ArrayList<>(roomChores);
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
            choresList.setItems(FXCollections.observableArrayList(formattedChores));
            choresSummaryList.setItems(FXCollections.observableArrayList(formattedChores));

            List<Expense> roomExpenses = expenseManager.getRoomExpenses(appContext.getActiveRoom().getRoomID());
            List<Expense> expenses = new ArrayList<>(roomExpenses);
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
            paidExpensesForPie.clear();
            for (Expense expense : roomExpenses) {
                if (expense.isRecurring() && !expense.isPaidForCurrentCycle()) {
                    continue;
                }
                if (!expense.isPaid()) {
                    continue;
                }
                if (expense.getPaidByUserID() == null || expense.getPaidByUserID().isBlank()) {
                    continue;
                }
                paidExpensesForPie.add(expense);
                for (Map.Entry<String, Double> split : expense.getCustomSplitPercentages().entrySet()) {
                    double shareAmount = expense.getAmount() * (split.getValue() / 100.0);
                    spentByUser.put(
                            split.getKey(),
                            spentByUser.getOrDefault(split.getKey(), 0.0) + shareAmount
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
        String paidLabel = isSinglePersonPaying(expense)
                ? "Paid by " + userName(expense.getPaidByUserID())
                : "Paid";
        return expense.getDescription() + " | $" + String.format("%.2f", expense.getAmount()) + " | " + paidLabel;
    }

    private String formatRecurringExpense(Expense expense) {
        String due = expense.getNextDueAt() == null
                ? "No due date"
                : DATE_FORMAT.format(expense.getNextDueAt().atZone(ZoneId.systemDefault()));
        String state = expense.isPaidForCurrentCycle()
                ? (isSinglePersonPaying(expense) ? "Paid by " + userName(expense.getPaidByUserID()) : "Paid")
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

        choresList.setCellFactory(list -> new StatusAwareCell());
        choresSummaryList.setCellFactory(list -> new StatusAwareCell());
        balancesSummaryList.setCellFactory(list -> new BalanceCell());

        expensesList.setOnMouseClicked(event -> {
            int idx = expensesList.getSelectionModel().getSelectedIndex();
            if (idx < 0 || idx >= displayedExpenses.size()) {
                return;
            }
            showExpenseSplitDetails(displayedExpenses.get(idx));
        });
    }

    private void showExpenseSplitDetails(Expense expense) {
        String splitDetails = expense.getCustomSplitPercentages().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> {
                    double owed = expense.getAmount() * (entry.getValue() / 100.0);
                    return userName(entry.getKey()) + " -> " + String.format("%.1f%% ($%.2f)", entry.getValue(), owed);
                })
                .collect(Collectors.joining("\n"));
        Alert alert = new Alert(
                Alert.AlertType.INFORMATION,
                "Expense: " + expense.getDescription()
                        + "\nAmount: $" + String.format("%.2f", expense.getAmount())
                        + "\nStatus: " + buildPaidStatusText(expense)
                        + "\n\nSplit:\n" + splitDetails
        );
        alert.setHeaderText("Expense Split Details");
        alert.showAndWait();
    }

    private String buildPaidStatusText(Expense expense) {
        if (!expense.isPaid()) {
            return "Unpaid";
        }
        if (isSinglePersonPaying(expense)) {
            return "Paid by " + userName(expense.getPaidByUserID());
        }
        return "Paid";
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
                .filter(expense -> expense.getCustomSplitPercentages().containsKey(userId))
                .collect(Collectors.toList());
        List<String> paidOn = personPaidExpenses.stream()
                .map(expense -> {
                    double pct = expense.getCustomSplitPercentages().getOrDefault(userId, 0.0);
                    double share = expense.getAmount() * (pct / 100.0);
                    return "- " + expense.getDescription() + " ($" + String.format("%.2f", share) + ")";
                })
                .collect(Collectors.toList());
        double totalSpent = personPaidExpenses.stream()
                .mapToDouble(expense -> expense.getAmount() * (expense.getCustomSplitPercentages().getOrDefault(userId, 0.0) / 100.0))
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
        return appContext.getActiveMembers().stream()
                .filter(u -> u.getUserID().equals(userId))
                .findFirst()
                .map(User::getName)
                .orElse(userId);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("CoHabit Error");
        alert.showAndWait();
    }
}
