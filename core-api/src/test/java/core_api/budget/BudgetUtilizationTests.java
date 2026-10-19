package core_api.budget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetUtilizationTests {
    @ParameterizedTest
    @CsvSource({
            "0,100,80,SAFE,0.00,100.00",
            "79.99,100,80,SAFE,79.99,20.01",
            "80,100,80,WARNING,80.00,20.00",
            "99.99,100,80,WARNING,99.99,0.01",
            "100,100,80,EXCEEDED,100.00,0.00",
            "123.45,100,80,EXCEEDED,123.45,-23.45",
            "1,3,33,WARNING,33.33,2.00",
            "7999.99,10000,80,SAFE,80.00,2000.01",
            "9999.99,10000,80,WARNING,100.00,0.01"
    })
    void exactThresholdsAndHalfUpDisplayRounding(String spent, String limit, int threshold, String status, String percent, String remaining) {
        var usage = BudgetUtilization.calculate(new BigDecimal(limit), new BigDecimal(spent), threshold);
        assertThat(usage.budgetStatus()).isEqualTo(BudgetStatus.valueOf(status));
        assertThat(usage.percentageUsed()).isEqualTo(new BigDecimal(percent));
        assertThat(usage.remainingAmount()).isEqualTo(new BigDecimal(remaining));
    }

    @Test
    void emptySumIsZeroAndDecemberRollsToNextYear() {
        assertThat(BudgetUtilization.calculate(new BigDecimal("100.00"), null, 80).spentAmount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(new BudgetPeriod(12, 2100).endExclusive()).isEqualTo(java.time.LocalDate.of(2101, 1, 1));
    }
}
