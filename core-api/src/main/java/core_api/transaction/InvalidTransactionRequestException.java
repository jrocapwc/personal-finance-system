package core_api.transaction;

public class InvalidTransactionRequestException extends RuntimeException {
    public InvalidTransactionRequestException(String message) { super(message); }
}
