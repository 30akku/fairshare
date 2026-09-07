package com.anshi.fairshare.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "FairShare API", version = "v1", description = "Group expense sharing with multiple payers and deterministic splits"),
        security = @SecurityRequirement(name = "firebaseBearer")
)
@SecurityScheme(name = "firebaseBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "Firebase ID token")
public class OpenApiConfig {}

