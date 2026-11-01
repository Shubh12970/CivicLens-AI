package com.civiclens.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI civicLensOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CivicLens AI API")
                        .version("1.0.0")
                        .description("Transparent AI-assisted civic grievance and infrastructure triage. "
                                + "Departments are internal triage queues, not live municipal integrations. "
                                + "Use the Authorize button with a token from POST /api/v1/auth/login."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
