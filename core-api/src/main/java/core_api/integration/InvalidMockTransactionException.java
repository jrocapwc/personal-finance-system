package core_api.integration;

public class InvalidMockTransactionException extends RuntimeException {
    public InvalidMockTransactionException() { super("Invalid mock transaction"); }
}
