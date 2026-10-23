package core_api.integration;

import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MockTransactionClientTests {
    MockRestServiceServer server;
    MockTransactionClient client;
    static final String KEY = "test-only-integration-secret-not-for-real-use";
    static final String ROW = """
            {"externalTransactionId":"fictional-1","type":"INCOME","amount":"12345.67","description":"Fictional salary",
             "transactionDate":"2026-10-01","categoryHint":"SALARY","source":"MOCK_PROVIDER"}
            """;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://127.0.0.1:3001");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MockTransactionClient(builder.build(), properties(KEY));
    }
    static IntegrationProperties properties(String key) {
        return new IntegrationProperties(URI.create("http://127.0.0.1:3001"), key, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
    @Test void sendsOnlyDedicatedInternalCredentialAndDecodesDecimalStrings() {
        server.expect(requestTo("http://127.0.0.1:3001/internal/mock-transactions"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-Internal-API-Key", KEY))
                .andExpect(headerDoesNotExist("Authorization")).andExpect(headerDoesNotExist("X-Owner-Id"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andRespond(withSuccess("{\"transactions\":[" + ROW + "]}", MediaType.APPLICATION_JSON));
        var rows = client.fetch();
        assertThat(rows).hasSize(1);
        assertThat(MockTransaction.decode(rows.getFirst()).amount()).isEqualByComparingTo("12345.67");
        server.verify();
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 500, 503, 301, 400})
    void safeUpstreamStatusMapping(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)).body("private-upstream-marker"));
        assertThatThrownBy(client::fetch).isInstanceOfSatisfying(IntegrationException.class, error -> {
            assertThat(error.status()).isEqualTo(status >= 500 ? 503 : 502);
            assertThat(error.getMessage()).doesNotContain(KEY, "private-upstream-marker");
        });
    }
    @Test void unavailableAndTimeoutHaveDistinctSafeErrors() {
        server.expect(anything()).andRespond(withException(new ConnectException("private-marker")));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private-marker")));
        assertThatThrownBy(client::fetch).isInstanceOfSatisfying(IntegrationException.class, error -> assertThat(error.status()).isEqualTo(503));
        assertThatThrownBy(client::fetch).isInstanceOfSatisfying(IntegrationException.class, error -> assertThat(error.status()).isEqualTo(504));
    }
    @ParameterizedTest @ValueSource(strings = {"", "{", "null", "[]", "{}", "{\"transactions\":null}",
            "{\"transactions\":[],\"ownerId\":\"other\"}", "{\"transactions\":[]} {}"})
    void malformedEnvelopesAreRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::fetch).isInstanceOf(IntegrationException.class);
    }
    @Test void wrongContentTypeOversizedBodyAndTooManyRecordsAreRejected() {
        server.expect(anything()).andRespond(withSuccess("{\"transactions\":[]}", MediaType.TEXT_HTML));
        server.expect(anything()).andRespond(withSuccess(" ".repeat(256 * 1024 + 1), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"transactions\":[" + "{},".repeat(100) + "{}]}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 3; i++) assertThatThrownBy(client::fetch).isInstanceOf(IntegrationException.class);
    }
    @Test void missingSecretDoesNotSendRequest() {
        var builder = RestClient.builder();
        var noRequests = MockRestServiceServer.bindTo(builder).build();
        assertThatThrownBy(() -> new MockTransactionClient(builder.build(), properties("")).fetch()).isInstanceOf(IntegrationException.class);
        noRequests.verify();
    }
}
