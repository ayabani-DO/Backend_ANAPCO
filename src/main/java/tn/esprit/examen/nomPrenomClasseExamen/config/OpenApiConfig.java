package tn.esprit.examen.nomPrenomClasseExamen.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI documentation of the JWT bearer authentication.
 *
 * <p>This is <b>documentation only</b> — it adds a {@code bearerAuth} security scheme to the
 * generated {@code /v3/api-docs} document so Swagger UI renders an <b>Authorize</b> button and
 * attaches {@code Authorization: Bearer <token>} to "Try it out" calls. It does <b>not</b> change
 * Spring Security: runtime enforcement still lives entirely in {@code SecurityConfig} / {@code JwtFilter}.
 *
 * <p>The global {@code security} on {@link OpenAPIDefinition} applies the requirement to every
 * operation; the public {@code /auth/**} endpoints simply ignore the extra header.
 *
 * <p>In the Authorize dialog paste the <b>raw JWT only</b> (no {@code "Bearer "} prefix) — Swagger
 * UI adds the {@code Bearer } prefix itself because the scheme is {@code type = http, scheme = bearer}.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@OpenAPIDefinition(
        info = @Info(title = "ANAPCO API", version = "v1"),
        security = @SecurityRequirement(name = "bearerAuth")
)
public class OpenApiConfig {
}
