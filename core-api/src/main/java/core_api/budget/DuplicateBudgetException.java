package core_api.budget;

public class DuplicateBudgetException extends RuntimeException {
    public DuplicateBudgetException() { super("A budget already exists for this category, month, and year"); }
}
