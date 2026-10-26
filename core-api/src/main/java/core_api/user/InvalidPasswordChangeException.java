package core_api.user;

public class InvalidPasswordChangeException extends RuntimeException {
    public InvalidPasswordChangeException() { super("Password change rejected"); }
}
