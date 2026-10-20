package core_api.dashboard;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialSummaryTests {
    @ParameterizedTest
    @CsvSource({"100,25,75.00,75.00", "100,150,-50.00,-50.00", "3,2,1.00,33.33",
            "3,1,2.00,66.67", "0,0,0.00,NULL", "0,20,-20.00,NULL", "0.30,0.10,0.20,66.67",
            "99999999999999999.99,0.01,99999999999999999.98,100.00"})
    void usesExactMoneyAndHalfUpPercentagesIncludingZeroAndNegativeSavings(String income, String expenses, String savings, String rate) {
        var result = FinancialSummary.from(new BigDecimal(income), new BigDecimal(expenses));
        assertThat(result.netSavings()).isEqualTo(new BigDecimal(savings));
        if (rate.equals("NULL")) { assertThat(result.savingsRate()).isNull(); }
        else { assertThat(result.savingsRate()).isEqualTo(new BigDecimal(rate)); }
    }
}
