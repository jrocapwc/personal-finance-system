package core_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI coreApiDocumentation() {
        return new OpenAPI()
                .info(new Info().title("Personal Finance Core API").version("v1")
                        .description("Register, log in, and manage your categories, transactions, and live monthly expense budgets. "
                                + "Log in to obtain a 60-minute access token, then paste it into Authorize."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Paste the accessToken returned by login, without the Bearer prefix.")));
    }
}
