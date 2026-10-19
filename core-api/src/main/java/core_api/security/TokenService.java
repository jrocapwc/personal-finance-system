package core_api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    public static final Duration LIFETIME = Duration.ofMinutes(60);
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.issuer}") String issuer,
                        @Value("${app.jwt.audience}") String audience) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(UUID userId) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(userId.toString()).issuedAt(now).notBefore(now)
                .expiresAt(now.plus(LIFETIME)).id(UUID.randomUUID().toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
