# CoHabit

CoHabit is a JavaFX desktop coordinator for shared living spaces. It supports room setup, chore tracking, and expense balancing with cloud persistence to Firebase Firestore and automatic fallback to local JSON storage.

## Stack

- Java 17
- JavaFX
- Firebase Firestore (REST API)
- Local JSON fallback (Jackson)
- JUnit 5

## Project Structure

```text
src/main/java/cohabit/
  model/      Room, User, Chore, Expense
  core/       RoomManager, ChoreManager, ExpenseManager
  firebase/   FirebaseService + Firestore REST + local fallback
  ui/         JavaFX controllers
  Main.java   app entry point

src/main/resources/
  fxml/       JavaFX layout files
  local/      fallback JSON store
```

## Firebase Configuration

Set these environment variables before launching to enable Firestore:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_API_KEY`

If either variable is missing or Firebase cannot be reached, CoHabit transparently uses `src/main/resources/local/data.json`.

## Run

```bash
mvn clean javafx:run
```

## Tests

```bash
mvn test
```
