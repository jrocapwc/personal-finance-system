package core_api.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTrend(LocalDate date, BigDecimal amount) {
}
