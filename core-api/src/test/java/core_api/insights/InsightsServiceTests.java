package core_api.insights;

import core_api.dashboard.DashboardService;
import core_api.budget.InvalidBudgetRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTests {
    @Mock DashboardService dashboard;
    @Mock FinancialInsightsClient client;
    @InjectMocks InsightsService insights;

    @Test void obtainsOnlyVerifiedOwnersDashboardAndMapsWithoutChangingMoney() {
        var owner = UUID.randomUUID();
        var response = mock(InsightsResponse.class);
        when(dashboard.get(owner, 10, 2026)).thenReturn(InsightsFixtures.dashboard());
        var expected = AiFinancialRequest.from(InsightsFixtures.dashboard());
        when(client.analyze(expected)).thenReturn(response);
        assertThat(insights.get(owner, 10, 2026)).isSameAs(response);
        verify(dashboard).get(owner, 10, 2026);
        verify(client).analyze(expected);
        verifyNoMoreInteractions(dashboard, client);
    }
    @Test void invalidMonthIsRejectedBeforeQueriesOrUpstreamCall() {
        assertThatThrownBy(() -> insights.get(UUID.randomUUID(), 13, 2026)).isInstanceOf(InvalidBudgetRequestException.class);
        verifyNoInteractions(dashboard, client);
    }
}
