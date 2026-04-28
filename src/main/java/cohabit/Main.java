package cohabit;

import cohabit.core.AppContext;
import cohabit.core.ChoreManager;
import cohabit.core.ExpenseManager;
import cohabit.core.RoomManager;
import cohabit.firebase.FirebaseService;
import cohabit.ui.MainController;
import cohabit.ui.RoomSetupController;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Main extends Application {
    private final AppContext appContext = new AppContext();
    private FirebaseService firebaseService;
    private RoomManager roomManager;
    private ChoreManager choreManager;
    private ExpenseManager expenseManager;
    private MainController mainController;

    @Override
    public void start(Stage primaryStage) throws Exception {
        firebaseService = new FirebaseService(Path.of("src/main/resources/local/data.json"));
        roomManager = new RoomManager(firebaseService);
        choreManager = new ChoreManager(firebaseService);
        expenseManager = new ExpenseManager(firebaseService);
        primaryStage.setTitle("CoHabit");
        if (!initializeSession(primaryStage)) {
            primaryStage.close();
            return;
        }
        loadMainWorkspace(primaryStage);
    }

    private void loadMainWorkspace(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 780);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        mainController = loader.getController();
        mainController.init(appContext, choreManager, expenseManager, firebaseService);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true); // windowed fullscreen
        primaryStage.show();
    }

    private boolean showRoomSetup(Stage owner) throws IOException {
        String previousUserId = appContext.getActiveUser() != null ? appContext.getActiveUser().getUserID() : null;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/room-setup.fxml"));
        Scene scene = new Scene(loader.load(), 520, 460);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        RoomSetupController controller = loader.getController();
        controller.init(roomManager, appContext, null);

        Stage setupStage = new Stage();
        setupStage.setTitle("Room Setup");
        setupStage.setScene(scene);
        if (owner != null) {
            setupStage.initOwner(owner);
        }
        setupStage.initModality(Modality.APPLICATION_MODAL);
        setupStage.showAndWait();

        if (appContext.getActiveUser() == null) {
            return false;
        }
        if (previousUserId == null) {
            return true;
        }
        return !previousUserId.equals(appContext.getActiveUser().getUserID());
    }

    private boolean initializeSession(Stage owner) {
        try {
            while (true) {
                SessionAction action = promptSessionAction();
                if (action == SessionAction.EXIT) {
                    return false;
                }

                if (action == SessionAction.CREATE_ACCOUNT) {
                    if (!showRoomSetup(owner)) {
                        continue;
                    }
                    return true;
                }

                Optional<LoginSelection> loginSelection = promptForExistingLogin();
                if (loginSelection.isEmpty()) {
                    continue;
                }
                LoginSelection selection = loginSelection.get();
                appContext.setActiveUser(selection.user());
                appContext.setActiveRoom(selection.room());
                appContext.setActiveMembers(roomManager.getRoomUsers(selection.room()));
                return true;
            }
        } catch (IOException | InterruptedException ex) {
            showFatalError("Unable to load account data: " + ex.getMessage());
            return false;
        }
    }

    private SessionAction promptSessionAction() {
        ButtonType loginButton = new ButtonType("Log In", ButtonBar.ButtonData.OK_DONE);
        ButtonType createButton = new ButtonType("Create Account", ButtonBar.ButtonData.OTHER);
        ButtonType exitButton = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Choose how you want to start.",
                loginButton,
                createButton,
                exitButton
        );
        alert.setTitle("Welcome to CoHabit");
        alert.setHeaderText("Log in or create a new account");
        applyAppStyles(alert.getDialogPane());
        ButtonType chosen = alert.showAndWait().orElse(exitButton);
        if (chosen == loginButton) {
            return SessionAction.LOGIN;
        }
        if (chosen == createButton) {
            return SessionAction.CREATE_ACCOUNT;
        }
        return SessionAction.EXIT;
    }

    private Optional<LoginSelection> promptForExistingLogin() throws IOException, InterruptedException {
        while (true) {
            Dialog<ButtonType> loginDialog = new Dialog<>();
            loginDialog.setTitle("Log In");
            loginDialog.setHeaderText("Enter room username and room password");
            applyAppStyles(loginDialog.getDialogPane());

            ButtonType loginButton = new ButtonType("Log In", ButtonBar.ButtonData.OK_DONE);
            loginDialog.getDialogPane().getButtonTypes().addAll(loginButton, ButtonType.CANCEL);

            TextField roomUsernameField = new TextField();
            roomUsernameField.setPromptText("Room username");
            PasswordField roomPasswordField = new PasswordField();
            roomPasswordField.setPromptText("Room password");

            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(10);
            form.add(new Label("Room Username"), 0, 0);
            form.add(roomUsernameField, 1, 0);
            form.add(new Label("Room Password"), 0, 1);
            form.add(roomPasswordField, 1, 1);
            loginDialog.getDialogPane().setContent(form);

            ButtonType result = loginDialog.showAndWait().orElse(ButtonType.CANCEL);
            if (result != loginButton) {
                return Optional.empty();
            }

            String roomUsername = roomUsernameField.getText().trim();
            String roomPassword = roomPasswordField.getText();
            if (roomUsername.isBlank() || roomPassword == null || roomPassword.isBlank()) {
                continue;
            }

            Optional<LoginSelection> match = findLoginSelectionForRoom(roomUsername, roomPassword);
            if (match.isEmpty()) {
                showLoginError();
                continue;
            }
            return match;
        }
    }

    private void applyAppStyles(DialogPane pane) {
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        pane.getStyleClass().add("cohabit-dialog");
    }

    private Optional<LoginSelection> findLoginSelectionForRoom(String roomUsername, String roomPassword) throws IOException, InterruptedException {
        Optional<cohabit.model.Room> room = roomManager.findRoomByUsernameAndPassword(roomUsername, roomPassword);
        if (room.isEmpty()) {
            return Optional.empty();
        }
        List<cohabit.model.User> members = roomManager.getRoomUsers(room.get());
        if (members.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LoginSelection(members.get(0), room.get()));
    }

    private void showLoginError() {
        Alert alert = new Alert(Alert.AlertType.ERROR, "Room username/password not found.");
        alert.setHeaderText("Login failed");
        alert.showAndWait();
    }

    private void showFatalError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Startup Error");
        alert.showAndWait();
    }

    private record LoginSelection(cohabit.model.User user, cohabit.model.Room room) {
        @Override
        public String toString() {
            return room.getName() + " (" + user.getName() + ")";
        }
    }

    private enum SessionAction {
        LOGIN,
        CREATE_ACCOUNT,
        EXIT
    }

    public static void main(String[] args) {
        launch(args);
    }
}
