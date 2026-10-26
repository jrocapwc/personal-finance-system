package core_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class BrowserCredentialsTests {
    @Test void productionCannotDisableSecureOrUseInvalidCookieNames() {
        var environment = new MockEnvironment();
        assertThatThrownBy(() -> new BrowserCredentials("finance_session", false, environment)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BrowserCredentials("invalid;name", true, environment)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new BrowserCredentials("__Host-finance_session", true, environment)).doesNotThrowAnyException();
    }
    @Test void onlyExplicitLocalProfilePermitsPlainHttpAndHostPrefixStillRequiresSecure() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(() -> new BrowserCredentials("finance_session", false, environment)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new BrowserCredentials("__Host-finance_session", false, environment)).isInstanceOf(IllegalStateException.class);
    }
}
