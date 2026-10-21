package core_api.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FinancialInsightsClientTests {
    private MockRestServiceServer server;
    private FinancialInsightsClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://127.0.0.1:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FinancialInsightsClient(builder.build(), InsightsFixtures.properties(InsightsFixtures.KEY));
    }

    @Test void postsExactMinimalContractAsDecimalStringsAndOnlyInternalCredential() {
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/insights/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-API-Key", InsightsFixtures.KEY))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andExpect(request -> assertThat(json.readTree(((MockClientHttpRequest) request).getBodyAsString()))
                        .isEqualTo(json.readTree(InsightsFixtures.REQUEST)))
                .andRespond(withSuccess(InsightsFixtures.RESPONSE, MediaType.APPLICATION_JSON));
        var response = client.analyze(AiFinancialRequest.from(InsightsFixtures.dashboard()));
        assertThat(response.summary()).isEqualTo("Monthly overview");
        assertThat(response.positiveObservations()).containsExactly("Positive savings");
        assertThat(response.concerns()).isEmpty();
        assertThat(response.recommendations()).containsExactly("Review spending");
        assertThat(response.suggestedActions()).isEmpty();
        assertThat(response.disclaimer()).contains("Educational");
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {301, 307, 400, 401, 403, 422, 500, 503})
    void upstreamStatusesBecomeSanitizedGatewayFailures(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .body("secret-upstream-body " + InsightsFixtures.KEY).contentType(MediaType.TEXT_PLAIN));
        assertFailure(false);
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"", "{", "null", "[]", "{}",
            "{\"summary\":\"partial\"}",
            "{\"summary\":123,\"positiveObservations\":[],\"concerns\":[],\"recommendations\":[],\"suggestedActions\":[],\"disclaimer\":\"educational\"}",
            "{\"summary\":\"overview\",\"positiveObservations\":[123],\"concerns\":[],\"recommendations\":[],\"suggestedActions\":[],\"disclaimer\":\"educational\"}",
            "{\"summary\":\"overview\",\"positiveObservations\":null,\"concerns\":[],\"recommendations\":[],\"suggestedActions\":[],\"disclaimer\":\"educational\"}"})
    void malformedOrIncompleteOrWrongTypedResponsesAreRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertFailure(false);
        server.verify();
    }

    @Test void extraFieldsOrTrailingJsonAreRejected() {
        server.expect(anything()).andRespond(withSuccess(InsightsFixtures.RESPONSE.replace("\"summary\":", "\"internalApiKey\":\"secret\",\"summary\":"), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(InsightsFixtures.RESPONSE + " {}", MediaType.APPLICATION_JSON));
        assertFailure(false);
        assertFailure(false);
        server.verify();
    }

    @Test void wrongContentTypeAndOversizedResponseAreRejected() {
        server.expect(anything()).andRespond(withSuccess(InsightsFixtures.RESPONSE, MediaType.TEXT_HTML));
        server.expect(anything()).andRespond(withSuccess(" ".repeat(1024 * 1024 + 1), MediaType.APPLICATION_JSON));
        assertFailure(false);
        assertFailure(false);
        server.verify();
    }

    @Test void unavailableServiceIs502Failure() {
        server.expect(anything()).andRespond(withException(new ConnectException("private connection detail")));
        assertFailure(false);
        server.verify();
    }

    @Test void responseTimeoutIsClassifiedSeparately() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private timeout detail")));
        assertFailure(true);
        server.verify();
    }

    @Test void nestedConnectionTimeoutIsClassifiedSeparately() {
        server.expect(anything()).andRespond(withException(new IOException("private", new HttpConnectTimeoutException("private"))));
        assertFailure(true);
        server.verify();
    }

    @Test void missingConfigurationFailsWithoutMakingHttpRequest() {
        var builder = RestClient.builder();
        var noRequests = MockRestServiceServer.bindTo(builder).build();
        var missingKey = new FinancialInsightsClient(builder.build(), InsightsFixtures.properties(""));
        assertThatThrownBy(() -> missingKey.analyze(AiFinancialRequest.from(InsightsFixtures.dashboard())))
                .isInstanceOf(UpstreamInsightsException.class).hasMessage("Financial insights service request failed");
        noRequests.verify();
    }

    @Test void configurationIsRedactedAndRejectsUnsafeOriginsAndUnboundedTimeouts() {
        assertThat(InsightsFixtures.properties(InsightsFixtures.KEY).toString()).doesNotContain(InsightsFixtures.KEY);
        for (var uri : java.util.List.of("file:///tmp", "http://user:password@localhost", "http://localhost/?token=secret", "http://localhost/path")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AiServiceProperties(java.net.URI.create(uri), "", java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5)));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new AiServiceProperties(java.net.URI.create("http://localhost"), "", java.time.Duration.ZERO, java.time.Duration.ofSeconds(5)));
    }

    @Test void chatForwardsOnlyExpectedContextAndInternalKey() {
        server.expect(requestTo("http://127.0.0.1:8000/api/v1/chat/respond"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-API-Key", InsightsFixtures.KEY))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andExpect(request -> {
                    var root = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(root.size()).isEqualTo(4);
                    assertThat(root.get("question").asString()).isEqualTo("Explain my savings");
                    assertThat(root.get("month").asInt()).isEqualTo(10);
                    assertThat(root.get("year").asInt()).isEqualTo(2026);
                    assertThat(root.get("financialSummary")).isEqualTo(json.readTree(InsightsFixtures.REQUEST));
                })
                .andRespond(withSuccess("{\"answer\":\"Savings were 150.00.\",\"provider\":\"rule-based\",\"disclaimer\":\"Educational only\"}", MediaType.APPLICATION_JSON));
        var response = client.chat(new core_api.chat.ChatUpstreamRequest("Explain my savings", 10, 2026,
                AiFinancialRequest.from(InsightsFixtures.dashboard())));
        assertThat(response.provider()).isEqualTo("rule-based");
        assertThat(response.answer()).isEqualTo("Savings were 150.00.");
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "null",
            "{\"answer\":\"A\",\"provider\":\"unknown\",\"disclaimer\":\"D\"}",
            "{\"answer\":\"A\",\"provider\":\"openai\",\"disclaimer\":null}"})
    void invalidChatResponseIsAnUpstreamFailure(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.chat(new core_api.chat.ChatUpstreamRequest("Explain my savings", 10, 2026,
                AiFinancialRequest.from(InsightsFixtures.dashboard())))).isInstanceOf(UpstreamInsightsException.class);
        server.verify();
    }
    private void assertFailure(boolean timeout) {
        assertThatThrownBy(() -> client.analyze(AiFinancialRequest.from(InsightsFixtures.dashboard())))
                .isInstanceOfSatisfying(UpstreamInsightsException.class, exception -> {
                    assertThat(exception.isTimeout()).isEqualTo(timeout);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("private", "secret", InsightsFixtures.KEY);
                });
    }
}
