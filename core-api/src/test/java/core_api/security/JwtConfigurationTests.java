package core_api.security;

import core_api.JwtTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTests {
    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void validRandomKeyIsAccepted() {
        assertThat(configuration.jwtSigningKey(JwtTestSupport.newSecret()).getEncoded()).hasSize(32);
    }

    @Test
    void missingWeakOrInvalidKeyFailsWithoutEchoingSecret() {
        for (String secret : new String[]{"", "c2hvcnQ=", "not!base64"}) {
            assertThatThrownBy(() -> configuration.jwtSigningKey(secret))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET_BASE64");
        }
    }
}
