package core_api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public abstract class JwtTestSupport {
    private static final String SECRET = newSecret();
    private static final String DATABASE = "jdbc:h2:mem:auth-test-" + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @DynamicPropertySource
    static void authenticationProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> SECRET);
        registry.add("spring.datasource.url", () -> DATABASE);
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
