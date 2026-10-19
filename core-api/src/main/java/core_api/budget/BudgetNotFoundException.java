package core_api.budget;

public class BudgetNotFoundException extends RuntimeException {
    public BudgetNotFoundException() { super("Budget not found"); }
}
