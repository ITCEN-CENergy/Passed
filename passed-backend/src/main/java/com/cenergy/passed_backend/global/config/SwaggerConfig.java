package com.gooroomees.neulbomgil_backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        String jwtSchemeName = "JWT TOKEN";

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(jwtSchemeName);

        SecurityScheme securityScheme = new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(
                        new io.swagger.v3.oas.models.Components()
                                .addSecuritySchemes(jwtSchemeName, securityScheme)
                )
                .info(new Info()
                        .title("늘봄길 API")
                        .version("v1"));
    }
}