package tn.esprit.examen.nomPrenomClasseExamen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of CORS configuration, consumed by Spring Security's {@code .cors(withDefaults())}.
 *
 * <p>Publishing a {@link CorsConfigurationSource} bean (rather than a {@code CorsFilter} bean) means
 * CORS is applied exactly once, inside the security filter chain, with no duplicate
 * {@code Access-Control-Allow-Origin} header.
 *
 * <p>Allowed origins are configurable via {@code cors.allowed-origins} (comma-separated); never
 * {@code "*"} because credentials are allowed.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                HttpHeaders.ORIGIN,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.AUTHORIZATION,
                "X-Requested-With"));
        config.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
