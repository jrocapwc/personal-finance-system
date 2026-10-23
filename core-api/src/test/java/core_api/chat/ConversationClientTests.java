package core_api.chat;

import core_api.dashboard.DashboardService;
import core_api.insights.AiServiceProperties;
import core_api.insights.UpstreamInsightsException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ConversationClientTests {
    MockRestServiceServer server;
    ConversationClient client;
    UUID owner = UUID.randomUUID();
    String id = "0123456789abcdef01234567";
    String key = "test-only-internal-key-32-bytes-long";
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ConversationClient(builder.build(), new AiServiceProperties(URI.create("http://127.0.0.1:8000"),
                key, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }
    @Test void generalContinuationSendsTrustedIdentityAndNeverFetchesFinances() {
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/chat/conversations/" + id + "?page=0&size=1"))
                .andExpect(header("X-Owner-Id", owner.toString())).andExpect(header("X-Internal-API-Key", key))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"mode\":\"GENERAL\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/chat/conversations/" + id + "/messages"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-Owner-Id", owner.toString()))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().json("{\"question\":\"Continue\"}"))
                .andRespond(withSuccess("{\"answer\":\"A continuation\",\"provider\":\"openai\",\"disclaimer\":\"Verify\"}", MediaType.APPLICATION_JSON));
        var dashboard = mock(DashboardService.class);
        assertThat(new ConversationService(client, dashboard).send(owner, id, new ConversationMessageRequest("Continue")).answer())
                .isEqualTo("A continuation");
        verifyNoInteractions(dashboard);
        server.verify();
    }
    @ParameterizedTest @ValueSource(ints = {400, 404, 503})
    void knownErrorsHaveSafeCodes(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":{\"code\":\"PERSISTENCE_UNAVAILABLE\",\"message\":\"secret\"}}"));
        assertThatThrownBy(() -> client.exchange(owner, HttpMethod.GET, "", null))
                .isInstanceOfSatisfying(ConversationException.class, error -> assertThat(error.status()).isEqualTo(status))
                .hasMessageNotContaining("secret");
    }
    @Test void networkTimeoutIsSafe() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("secret")));
        assertThatThrownBy(() -> client.exchange(owner, HttpMethod.GET, "", null))
                .isInstanceOfSatisfying(ConversationException.class, error -> assertThat(error.status()).isEqualTo(504))
                .hasMessageNotContaining("secret");
    }
    @Test void invalidIdsAndPagesNeverReachUpstream() {
        var service = new ConversationService(client, mock(DashboardService.class));
        assertThatThrownBy(() -> service.read(owner, "invalid", 0, 20)).isInstanceOf(ConversationException.class);
        assertThatThrownBy(() -> service.list(owner, -1, 20)).isInstanceOf(ConversationException.class);
        server.verify();
    }
    @Test void inaccessibleConversationStopsBeforeDashboardAndAppend() {
        var dashboard = mock(DashboardService.class);
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> new ConversationService(client, dashboard).send(owner, id, new ConversationMessageRequest("hello")))
                .isInstanceOfSatisfying(ConversationException.class, error -> assertThat(error.status()).isEqualTo(404));
        verifyNoInteractions(dashboard);
        server.verify();
    }
    @Test void everyFinanceTurnRefreshesOwnedH2Summary() {
        var dashboard = mock(DashboardService.class);
        var upstream = mock(ConversationClient.class);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        when(upstream.exchange(eq(owner), eq(HttpMethod.GET), anyString(), isNull()))
                .thenReturn(json.readTree("{\"mode\":\"FINANCE\",\"month\":10,\"year\":2026}"));
        var empty = new core_api.dashboard.DashboardResponse(10, 2026,
                core_api.dashboard.FinancialSummary.from(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        when(dashboard.get(owner, 10, 2026)).thenReturn(empty);
        var service = new ConversationService(upstream, dashboard);
        service.send(owner, id, new ConversationMessageRequest("Explain savings"));
        service.send(owner, id, new ConversationMessageRequest("Explain budgets"));
        verify(dashboard, times(2)).get(owner, 10, 2026);
        var captured = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(upstream, times(2)).exchange(eq(owner), eq(HttpMethod.POST), eq("/" + id + "/messages"), captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(body -> {
            assertThat(body).isInstanceOf(ConversationUpstreamTurn.class);
            assertThat(((ConversationUpstreamTurn) body).financialSummary().month()).isEqualTo(10);
        });
    }
}
