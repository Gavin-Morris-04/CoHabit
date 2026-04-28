# CoHabit

CoHabit is a JavaFX desktop coordinator for shared living spaces. It supports room setup, chore tracking, and expense balancing with cloud persistence to Firebase Firestore and automatic fallback to local JSON storage.

## What You Need

Install these before running CoHabit:

- Java 17 (JDK, not just JRE)
- Maven 3.9+
- Git

## Tech Stack

- Java 17
- JavaFX
- Firebase Firestore (REST API)
- Local JSON fallback (Jackson)
- JUnit 5
- Maven build and run tooling

## Quick Start

### 1) Clone the project

```bash
git clone https://github.com/<your-org-or-user>/CoHabit.git
cd CoHabit
```

### 2) Verify Java and Maven

```bash
java -version
mvn -version
```

Expected:
- Java should report version 17
- Maven should report a valid installed version

### 3) Run the app

```bash
mvn clean javafx:run
```

### 4) Run tests

```bash
mvn test
```

---

## Firebase Setup (Optional but Recommended)

CoHabit can run with Firestore cloud persistence, but if Firebase is not configured or unavailable, it automatically falls back to local JSON storage.

Set these environment variables:

- `FIREBASE_PROJECT_ID`
- `FIREBASE_API_KEY`

### Windows PowerShell (current terminal session only)

```powershell
$env:FIREBASE_PROJECT_ID="your-project-id"
$env:FIREBASE_API_KEY="your-api-key"
mvn clean javafx:run
```

### Windows Command Prompt (current terminal session only)

```cmd
set FIREBASE_PROJECT_ID=your-project-id
set FIREBASE_API_KEY=your-api-key
mvn clean javafx:run
```

### macOS/Linux (bash/zsh, current terminal session only)

```bash
export FIREBASE_PROJECT_ID="your-project-id"
export FIREBASE_API_KEY="your-api-key"
mvn clean javafx:run
```

If either variable is missing, or Firestore cannot be reached, CoHabit transparently uses:

`src/main/resources/local/data.json`

---

## Run Without Firebase (Local-Only Mode)

If you do not set Firebase environment variables, simply run:

```bash
mvn clean javafx:run
```

Data will be stored in the local JSON file.

---

## Project Structure

```text
src/main/java/cohabit/
  model/      Room, User, Chore, Expense
  core/       RoomManager, ChoreManager, ExpenseManager, AppContext
  firebase/   FirebaseService + Firestore REST + local fallback
  ui/         JavaFX controllers
  Main.java   app entry point

src/main/resources/
  fxml/       JavaFX layout files
  styles/     application styles
  local/      fallback JSON store

src/test/java/cohabit/
  core/       unit tests for manager logic
```

---

## Useful Development Commands

From the project root:

```bash
mvn clean                 # remove previous build output
mvn test                  # run unit tests
mvn clean test            # clean + test
mvn clean javafx:run      # start desktop app
mvn -q test               # run tests with quieter logs
```

---

## Troubleshooting

### `java` or `mvn` not recognized

- Java or Maven is not installed correctly, or not on `PATH`.
- Reopen terminal after installation and re-run:

```bash
java -version
mvn -version
```

### App runs but cannot connect to Firebase

- Verify `FIREBASE_PROJECT_ID` and `FIREBASE_API_KEY` are correct.
- Confirm the variables are set in the same shell session where you run Maven.
- CoHabit will continue in local JSON fallback mode.

### Java version is not 17

- Install JDK 17 and make it the active default.
- Then verify:

```bash
java -version
```

---

## License

Add your project license information here (for example, MIT, Apache-2.0, or proprietary).
