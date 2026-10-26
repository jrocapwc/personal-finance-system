package core_api;

import java.net.URI;
import java.net.http.*;

public final class CsrfTestSupport {
    private CsrfTestSupport() {}
    public static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow().split(";", 2)[0];
    }
    public static HttpRequest.Builder protect(HttpClient client, String base, HttpRequest.Builder builder) throws Exception {
        var bootstrap = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = cookie(bootstrap, "XSRF-TOKEN");
        return builder.header("Cookie", cookie).header("X-XSRF-TOKEN", cookie.substring("XSRF-TOKEN=".length()));
    }
}
