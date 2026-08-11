package com.securevault.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P5.8: Swagger UI's "Authorize" button needs the bearer scheme registered here to attach
 * `Authorization: Bearer &lt;token&gt;` automatically — obtain a token from POST /api/auth/login
 * first (or /mfa/challenge if MFA is enabled), then Authorize with just the raw token, no "Bearer "
 * prefix needed.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI secureVaultOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("SecureVault API")
                                .version("v1")
                                .description(
                                        "Password vault and credential management API — auth, vault CRUD, password"
                                                + " intelligence, sharing, MFA, security monitoring, notifications,"
                                                + " analytics, and admin operations.")
                                .contact(new Contact().name("SecureVault")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME_NAME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
