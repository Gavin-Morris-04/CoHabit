package cohabit.model;

public class User {
    private String userID;
    private String name;
    private String password;

    public User() {
    }

    public User(String userID, String name) {
        this.userID = userID;
        this.name = name;
        this.password = "";
    }

    public User(String userID, String name, String password) {
        this.userID = userID;
        this.name = name;
        this.password = password;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return name;
    }
}
