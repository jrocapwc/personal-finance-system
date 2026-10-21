package core_api.chat;

import core_api.dashboard.DashboardService;
import core_api.insights.FinancialInsightsClient;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ChatServiceTests {
    @Test void generalModeNeverRetrievesFinancialDataEvenWhenPeriodIsSupplied() {
        var dashboard = mock(DashboardService.class);
        var upstream = mock(FinancialInsightsClient.class);
        var service = new ChatService(dashboard, upstream);
        service.respond(UUID.randomUUID(), new ChatMessageRequest("Explain Python decorators", 10, 2026, ChatMode.GENERAL));
        verifyNoInteractions(dashboard);
        verify(upstream).chat(new ChatUpstreamRequest("Explain Python decorators", null, null, null, ChatMode.GENERAL));
    }
}
