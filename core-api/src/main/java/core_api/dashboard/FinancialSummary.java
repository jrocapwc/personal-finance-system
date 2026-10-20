package core_api.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record FinancialSummary(BigDecimal totalIncome, BigDecimal totalExpenses,
                               BigDecimal netSavings, BigDecimal savingsRate) {
    public static FinancialSummary from(BigDecimal income, BigDecimal expenses) {
        var totalIncome = income.setScale(2, RoundingMode.UNNECESSARY);
        var totalExpenses = expenses.setScale(2, RoundingMode.UNNECESSARY);
        var savings = totalIncome.subtract(totalExpenses);
        var rate = totalIncome.signum() == 0 ? null
                : savings.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 2, RoundingMode.HALF_UP);
        return new FinancialSummary(totalIncome, totalExpenses, savings, rate);
    }
}
