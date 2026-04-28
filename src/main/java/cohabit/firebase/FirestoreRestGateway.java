package cohabit.firebase;

import cohabit.model.Chore;
import cohabit.model.Expense;
import cohabit.model.Room;
import cohabit.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FirestoreRestGateway implements FirestoreGateway {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public FirestoreRestGateway(String projectId, String apiKey) {
        this.baseUrl = "https://firestore.googleapis.com/v1/projects/" + projectId + "/databases/(default)/documents";
        this.apiKey = apiKey;
    }

    @Override
    public Room saveRoom(Room room) throws IOException, InterruptedException {
        putDocument("rooms", room.getRoomID(), roomToFields(room));
        return room;
    }

    @Override
    public Optional<Room> getRoom(String roomId) throws IOException, InterruptedException {
        Optional<JsonNode> document = getDocument("rooms", roomId);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseRoom(document.get().path("fields")));
    }

    @Override
    public User saveUser(User user) throws IOException, InterruptedException {
        putDocument("users", user.getUserID(), userToFields(user));
        return user;
    }

    @Override
    public List<User> getUsersByIds(List<String> userIds) throws IOException, InterruptedException {
        List<User> users = new ArrayList<>();
        for (String userId : userIds) {
            Optional<JsonNode> document = getDocument("users", userId);
            document.ifPresent(doc -> users.add(parseUser(doc.path("fields"))));
        }
        return users;
    }

    @Override
    public List<User> getAllUsers() throws IOException, InterruptedException {
        return getCollection("users").stream()
                .map(doc -> parseUser(doc.path("fields")))
                .collect(Collectors.toList());
    }

    @Override
    public Chore saveChore(Chore chore) throws IOException, InterruptedException {
        putDocument("chores", chore.getChoreID(), choreToFields(chore));
        return chore;
    }

    @Override
    public List<Chore> getChoresByRoom(String roomId) throws IOException, InterruptedException {
        return getCollection("chores").stream()
                .map(doc -> parseChore(doc.path("fields")))
                .filter(chore -> roomId.equals(chore.getRoomID()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteChore(String choreId) throws IOException, InterruptedException {
        deleteDocument("chores", choreId);
    }

    @Override
    public Expense saveExpense(Expense expense) throws IOException, InterruptedException {
        putDocument("expenses", expense.getExpenseID(), expenseToFields(expense));
        return expense;
    }

    @Override
    public List<Expense> getExpensesByRoom(String roomId) throws IOException, InterruptedException {
        return getCollection("expenses").stream()
                .map(doc -> parseExpense(doc.path("fields")))
                .filter(expense -> roomId.equals(expense.getRoomID()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteExpense(String expenseId) throws IOException, InterruptedException {
        deleteDocument("expenses", expenseId);
    }

    @Override
    public List<Room> getAllRooms() throws IOException, InterruptedException {
        return getCollection("rooms").stream()
                .map(doc -> parseRoom(doc.path("fields")))
                .collect(Collectors.toList());
    }

    private void putDocument(String collection, String id, ObjectNode fields) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.set("fields", fields);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + collection + "/" + encode(id) + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Firestore write failed: " + response.body());
        }
    }

    private Optional<JsonNode> getDocument(String collection, String id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + collection + "/" + encode(id) + "?key=" + apiKey))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Firestore read failed: " + response.body());
        }
        return Optional.of(mapper.readTree(response.body()));
    }

    private void deleteDocument(String collection, String id) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + collection + "/" + encode(id) + "?key=" + apiKey))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Firestore delete failed: " + response.body());
        }
    }

    private List<JsonNode> getCollection(String collection) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + collection + "?key=" + apiKey))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return List.of();
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Firestore collection read failed: " + response.body());
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode documents = root.path("documents");
        if (!documents.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>();
        documents.forEach(list::add);
        return list;
    }

    private ObjectNode roomToFields(Room room) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("roomID", stringValue(room.getRoomID()));
        fields.set("roomUsername", stringValue(room.getRoomUsername() == null ? "" : room.getRoomUsername()));
        fields.set("roomPassword", stringValue(room.getRoomPassword() == null ? "" : room.getRoomPassword()));
        fields.set("name", stringValue(room.getName()));
        ArrayNode values = mapper.createArrayNode();
        for (String id : room.getMemberIds()) {
            values.add(stringValue(id));
        }
        ObjectNode array = mapper.createObjectNode();
        array.set("values", values);
        ObjectNode arrValue = mapper.createObjectNode();
        arrValue.set("arrayValue", array);
        fields.set("memberIds", arrValue);
        return fields;
    }

    private ObjectNode userToFields(User user) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("userID", stringValue(user.getUserID()));
        fields.set("name", stringValue(user.getName()));
        fields.set("password", stringValue(user.getPassword() == null ? "" : user.getPassword()));
        return fields;
    }

    private ObjectNode choreToFields(Chore chore) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("choreID", stringValue(chore.getChoreID()));
        fields.set("roomID", stringValue(chore.getRoomID()));
        fields.set("name", stringValue(chore.getName()));
        fields.set("assignedToUserID", stringValue(chore.getAssignedToUserID()));
        fields.set("status", stringValue(chore.getStatus()));
        fields.set("createdAt", timestampValue(chore.getCreatedAt()));
        if (chore.getCompletedAt() != null) {
            fields.set("completedAt", timestampValue(chore.getCompletedAt()));
        }
        return fields;
    }

    private ObjectNode expenseToFields(Expense expense) {
        ObjectNode fields = mapper.createObjectNode();
        fields.set("expenseID", stringValue(expense.getExpenseID()));
        fields.set("roomID", stringValue(expense.getRoomID()));
        fields.set("description", stringValue(expense.getDescription()));
        fields.set("paidByUserID", stringValue(expense.getPaidByUserID()));
        fields.set("amount", doubleValue(expense.getAmount()));
        fields.set("createdAt", timestampValue(expense.getCreatedAt()));
        fields.set("paid", booleanValue(expense.isPaid()));
        fields.set("recurring", booleanValue(expense.isRecurring()));
        fields.set("paidForCurrentCycle", booleanValue(expense.isPaidForCurrentCycle()));
        if (expense.getNextDueAt() != null) {
            fields.set("nextDueAt", timestampValue(expense.getNextDueAt()));
        }

        ObjectNode mapFields = mapper.createObjectNode();
        expense.getCustomSplitPercentages().forEach((k, v) -> mapFields.set(k, doubleValue(v)));
        ObjectNode mapValue = mapper.createObjectNode();
        mapValue.set("fields", mapFields);
        ObjectNode wrapped = mapper.createObjectNode();
        wrapped.set("mapValue", mapValue);
        fields.set("customSplitPercentages", wrapped);
        return fields;
    }

    private Room parseRoom(JsonNode fields) {
        List<String> members = new ArrayList<>();
        JsonNode values = fields.path("memberIds").path("arrayValue").path("values");
        if (values.isArray()) {
            values.forEach(v -> members.add(v.path("stringValue").asText()));
        }
        return new Room(
                fields.path("roomID").path("stringValue").asText(),
                fields.path("roomUsername").path("stringValue").asText(""),
                fields.path("roomPassword").path("stringValue").asText(""),
                fields.path("name").path("stringValue").asText(),
                members
        );
    }

    private User parseUser(JsonNode fields) {
        return new User(
                fields.path("userID").path("stringValue").asText(),
                fields.path("name").path("stringValue").asText(),
                fields.path("password").path("stringValue").asText("")
        );
    }

    private Chore parseChore(JsonNode fields) {
        Chore chore = new Chore();
        chore.setChoreID(fields.path("choreID").path("stringValue").asText());
        chore.setRoomID(fields.path("roomID").path("stringValue").asText());
        chore.setName(fields.path("name").path("stringValue").asText());
        chore.setAssignedToUserID(fields.path("assignedToUserID").path("stringValue").asText());
        chore.setStatus(fields.path("status").path("stringValue").asText());
        chore.setCreatedAt(Instant.parse(fields.path("createdAt").path("timestampValue").asText()));
        if (fields.has("completedAt")) {
            chore.setCompletedAt(Instant.parse(fields.path("completedAt").path("timestampValue").asText()));
        }
        return chore;
    }

    private Expense parseExpense(JsonNode fields) {
        Expense expense = new Expense();
        expense.setExpenseID(fields.path("expenseID").path("stringValue").asText());
        expense.setRoomID(fields.path("roomID").path("stringValue").asText());
        expense.setDescription(fields.path("description").path("stringValue").asText());
        expense.setPaidByUserID(fields.path("paidByUserID").path("stringValue").asText());
        expense.setAmount(fields.path("amount").path("doubleValue").asDouble());
        expense.setCreatedAt(Instant.parse(fields.path("createdAt").path("timestampValue").asText()));
        expense.setPaid(fields.path("paid").path("booleanValue").asBoolean(true));
        expense.setRecurring(fields.path("recurring").path("booleanValue").asBoolean(false));
        expense.setPaidForCurrentCycle(fields.path("paidForCurrentCycle").path("booleanValue").asBoolean(true));
        if (fields.has("nextDueAt")) {
            expense.setNextDueAt(Instant.parse(fields.path("nextDueAt").path("timestampValue").asText()));
        }

        JsonNode mapFields = fields.path("customSplitPercentages").path("mapValue").path("fields");
        Iterator<Map.Entry<String, JsonNode>> it = mapFields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            expense.getCustomSplitPercentages().put(entry.getKey(), entry.getValue().path("doubleValue").asDouble());
        }
        return expense;
    }

    private ObjectNode stringValue(String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("stringValue", value);
        return node;
    }

    private ObjectNode doubleValue(double value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("doubleValue", value);
        return node;
    }

    private ObjectNode timestampValue(Instant instant) {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestampValue", instant.toString());
        return node;
    }

    private ObjectNode booleanValue(boolean value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("booleanValue", value);
        return node;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
