package com.freightfox.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI freightFoxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FreightFox Transporter Assignment on Lanes API")
                        .description("Production-grade REST platform optimizing transporter assignments on trade lanes across India with cost minimization, transporter limits, and complete coverage constraints.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FreightFox Engineering")
                                .url("https://freightfox.ai")
                                .email("careers@freightfox.ai"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://spring.io/")))
                .servers(List.of(
                        new Server().url("/").description("Current Server (Local or Railway)")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token obtained from /api/v1/auth/login or /api/v1/auth/register")));
    }
}
