package core_api.security;

import core_api.error.SecurityErrorHandler;
import core_api.user.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(user -> new org.springframework.security.core.userdetails.User(
                        user.getEmail(), user.getPasswordHash(), List.of()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository(BrowserCredentials cookies) {
        return cookies.csrfRepository();
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http, SecurityErrorHandler errors,
                                           UserRepository users, BrowserCredentials cookies,
                                           org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                        // Resource Server otherwise adds an automatic CSRF exemption for
                        // every resolved bearer, including cookie credentials. Replace
                        // the final filter matcher with the explicit mechanism policy.
                        .withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.web.csrf.CsrfFilter>() {
                            @Override
                            public <O extends org.springframework.security.web.csrf.CsrfFilter> O postProcess(O filter) {
                                filter.setRequireCsrfProtectionMatcher(request ->
                                        org.springframework.security.web.csrf.CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                                                && !cookies.headerOnly(request));
                                return filter;
                            }
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors::unauthorized)
                        .accessDeniedHandler(errors::forbidden))
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(cookies::resolve)
                        .authenticationEntryPoint(errors::unauthorized)
                        .accessDeniedHandler(errors::forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            UUID userId;
                            try {
                                userId = UUID.fromString(token.getSubject());
                            } catch (IllegalArgumentException exception) {
                                throw new BadCredentialsException("Invalid token identity");
                            }
                            var user = users.findById(userId)
                                    .orElseThrow(() -> new BadCredentialsException("Invalid token identity"));
                            Object version = token.getClaims().get("token_version");
                            // Require an integer claim; legacy tokens without a version must log in again.
                            if (!(version instanceof Long || version instanceof Integer)
                                    || ((Number) version).longValue() != user.getTokenVersion()) {
                                throw new BadCredentialsException("Invalid token identity");
                            }
                            return new JwtAuthenticationToken(token, List.of(), userId.toString());
                        })))
                .build();
    }
}
