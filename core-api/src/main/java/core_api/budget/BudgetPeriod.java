package core_api.budget;

import java.time.LocalDate;

public record BudgetPeriod(int month, int year) {
    public BudgetPeriod {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new InvalidBudgetRequestException("Use month 1..12 and year 2000..2100");
        }
    }
    public LocalDate start() { return LocalDate.of(year, month, 1); }
    public LocalDate endExclusive() { return start().plusMonths(1); }
}
