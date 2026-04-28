package cohabit.ui;

import cohabit.core.AppContext;
import cohabit.core.RoomManager;
import cohabit.model.Room;
import cohabit.model.User;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RoomSetupController {
    @FXML
    private TextField yourNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField roomUsernameField;
    @FXML
    private TextField roomNameField;
    @FXML
    private TextArea roommatesArea;
    @FXML
    private Label statusLabel;

    private RoomManager roomManager;
    private AppContext appContext;
    private Runnable onSetupComplete;

    public void init(RoomManager roomManager, AppContext appContext, Runnable onSetupComplete) {
        this.roomManager = roomManager;
        this.appContext = appContext;
        this.onSetupComplete = onSetupComplete;
    }

    @FXML
    private void onCreateRoom() {
        try {
            List<String> names = Arrays.stream(roommatesArea.getText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
            Room room = roomManager.createRoom(
                    roomUsernameField.getText(),
                    roomNameField.getText(),
                    yourNameField.getText(),
                    passwordField.getText(),
                    names
            );
            List<User> members = roomManager.getRoomUsers(room);
            appContext.setActiveRoom(room);
            appContext.setActiveMembers(members);
            members.stream()
                    .filter(user -> user.getName().equalsIgnoreCase(yourNameField.getText().trim()))
                    .findFirst()
                    .ifPresent(appContext::setActiveUser);
            statusLabel.setText("Room created: " + room.getName());
            if (onSetupComplete != null) {
                onSetupComplete.run();
            }
            Stage stage = (Stage) roomNameField.getScene().getWindow();
            stage.close();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (IOException | InterruptedException ex) {
            showError("Failed to create room: " + ex.getMessage());
        }
    }

    @FXML
    private void onBack() {
        Stage stage = (Stage) roomNameField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("Room Setup Error");
        alert.showAndWait();
    }
}
