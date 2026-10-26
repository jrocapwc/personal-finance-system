package core_api.chat;

import core_api.JwtTestSupport;
import core_api.security.TokenService;
import core_api.user.User;
import core_api.user.UserRepository;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversationIntegrationTests extends JwtTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @MockitoBean ConversationClient client;
    String id = "0123456789abcdef01234567";
    JsonMapper json = JsonMapper.builder().build();

    @Test void everyEndpointRequiresJwt() throws Exception {
        assertThat(request("POST", "", "{\"mode\":\"GENERAL\"}", null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "", null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/" + id, null, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/" + id + "/messages", "{\"question\":\"hello\"}", null).statusCode()).isEqualTo(401);
        verifyNoInteractions(client);
    }
    @Test void ownerComesFromJwtAndCannotBeOverridden() throws Exception {
        var account = account();
        when(client.exchange(eq(account.getId()), eq(HttpMethod.POST), eq(""), any()))
                .thenReturn(json.readTree("{\"id\":\"" + id + "\",\"mode\":\"GENERAL\"}"));
        String token = tokens.issue(account.getId());
        var response = request("POST", "", "{\"mode\":\"GENERAL\"}", token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("id").asString()).isEqualTo(id);
        verify(client).exchange(eq(account.getId()), eq(HttpMethod.POST), eq(""), any());
        assertThat(request("POST", "", "{\"mode\":\"GENERAL\",\"ownerId\":\"other\"}", token).statusCode()).isEqualTo(400);
        assertThat(request("GET", "?ownerId=other", null, token).statusCode()).isEqualTo(400);
        assertThat(request("POST", "", "{\"mode\":\"FINANCE\"}", token).statusCode()).isEqualTo(400);
        assertThat(request("POST", "", "{\"mode\":\"INVALID\"}", token).statusCode()).isEqualTo(400);
    }
    @Test void inaccessibleConversationsReturn404ForReadAndAppend() throws Exception {
        var a = account();
        var b = account();
        when(client.exchange(eq(a.getId()), eq(HttpMethod.GET), anyString(), isNull()))
                .thenThrow(new ConversationException(404, "CONVERSATION_NOT_FOUND", "Conversation not found"));
        when(client.exchange(eq(b.getId()), eq(HttpMethod.GET), anyString(), isNull()))
                .thenReturn(json.readTree("{\"id\":\"" + id + "\",\"mode\":\"GENERAL\"}"));
        assertThat(request("GET", "/" + id, null, tokens.issue(b.getId())).statusCode()).isEqualTo(200);
        assertThat(request("GET", "/" + id, null, tokens.issue(a.getId())).statusCode()).isEqualTo(404);
        assertThat(request("POST", "/" + id + "/messages", "{\"question\":\"hello\"}", tokens.issue(a.getId())).statusCode()).isEqualTo(404);
        verify(client, never()).exchange(any(), eq(HttpMethod.POST), anyString(), any());
    }
    @Test void invalidInputAndStorageFailureUseApiEnvelope() throws Exception {
        var a = account();
        String token = tokens.issue(a.getId());
        assertThat(request("GET", "/invalid", null, token).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/" + id + "/messages", "{\"question\":\" \"}", token).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/" + id + "/messages", "{\"question\":\"" + "x".repeat(1001) + "\"}", token).statusCode()).isEqualTo(400);
        when(client.exchange(eq(a.getId()), eq(HttpMethod.GET), anyString(), isNull()))
                .thenThrow(new ConversationException(503, "PERSISTENCE_UNAVAILABLE", "Storage unavailable"));
        var response = request("GET", "", null, token);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("PERSISTENCE_UNAVAILABLE");
    }
    private User account() { return users.saveAndFlush(new User("Conversation test", UUID.randomUUID() + "@example.com", "test-hash")); }
    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/chat/conversations" + path))
                .header("Content-Type", "application/json");
        if (token == null && !method.equals("GET")) {
            try (var csrfClient = HttpClient.newHttpClient()) { core_api.CsrfTestSupport.protect(csrfClient, "http://127.0.0.1:" + port, builder); }
        }
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
