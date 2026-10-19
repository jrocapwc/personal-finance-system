package core_api.budget;

public class InvalidBudgetRequestException extends RuntimeException {
    public InvalidBudgetRequestException(String message) { super(message); }
}
