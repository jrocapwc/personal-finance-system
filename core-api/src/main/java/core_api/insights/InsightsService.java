package core_api.insights;

import core_api.budget.BudgetPeriod;
import core_api.dashboard.DashboardService;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class InsightsService {
    private final DashboardService dashboard;
    private final FinancialInsightsClient client;
    public InsightsService(DashboardService dashboard, FinancialInsightsClient client) {
        this.dashboard = dashboard; this.client = client;
    }
    public InsightsResponse get(UUID userId, int month, int year) {
        new BudgetPeriod(month, year);
        // Dashboard's database transaction finishes before the outbound network call.
        return client.analyze(AiFinancialRequest.from(dashboard.get(userId, month, year)));
    }
}
