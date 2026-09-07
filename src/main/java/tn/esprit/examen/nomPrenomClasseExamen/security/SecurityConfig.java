package tn.esprit.examen.nomPrenomClasseExamen.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;
import static tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles.ADMIN;
import static tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles.FINANCE_CONTROLLER;
import static tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles.OPS_MANAGER;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    /**
     * Endpoints that mutate operational data. Writes here are limited to ADMIN + OPS_MANAGER.
     * Reads (GET) on the same paths stay open to every authenticated role.
     */
    private static final String[] OPERATIONAL_WRITE_PATHS = {
            "/api/sites/**",
            "/api/Equipement/**",
            "/api/categorie/**",
            "/api/affectEquipementToCategorie/**",
            "/api/incidents/**",
            "/api/maintenance/**",
            "/api/weather-advisories/**",
            "/api/weather-data/**",
            "/api/weather-risk/**"
    };

    /**
     * Endpoints that mutate financial data. Writes here are limited to ADMIN + FINANCE_CONTROLLER.
     * Reads (GET) on the same paths stay open to every authenticated role.
     */
    private static final String[] FINANCE_WRITE_PATHS = {
            "/api/finance/budgets/**",
            "/api/finance/manual-expenses/**",
            "/api/finance/fx-rates/**",
            "/api/market-data/**"
    };

    private final JwtFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtFilter jwtAuthFilter,
                          AuthenticationProvider authenticationProvider,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> req
                        // ---- Public endpoints (no authentication) ----
                        .requestMatchers(
                                "/auth/authenticate",
                                "/auth/Register",
                                "/auth/activate-account/**",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/google",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ---- User & role administration: ADMIN only ----
                        // Roles are stored WITHOUT the "ROLE_" prefix, so use hasAuthority / hasAnyAuthority
                        // (hasRole would look for "ROLE_ADMIN" and never match).
                        .requestMatchers("/users/**", "/roles/**").hasAuthority(ADMIN)

                        // ---- Chatbot: any authenticated role (read-only data retrieval) ----
                        .requestMatchers("/api/assistant/**").authenticated()

                        // ---- Operational writes: ADMIN + OPS_MANAGER ----
                        .requestMatchers(HttpMethod.POST, OPERATIONAL_WRITE_PATHS).hasAnyAuthority(ADMIN, OPS_MANAGER)
                        .requestMatchers(HttpMethod.PUT, OPERATIONAL_WRITE_PATHS).hasAnyAuthority(ADMIN, OPS_MANAGER)
                        .requestMatchers(HttpMethod.DELETE, OPERATIONAL_WRITE_PATHS).hasAnyAuthority(ADMIN, OPS_MANAGER)
                        .requestMatchers(HttpMethod.PATCH, OPERATIONAL_WRITE_PATHS).hasAnyAuthority(ADMIN, OPS_MANAGER)

                        // ---- Financial writes: ADMIN + FINANCE_CONTROLLER ----
                        .requestMatchers(HttpMethod.POST, FINANCE_WRITE_PATHS).hasAnyAuthority(ADMIN, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.PUT, FINANCE_WRITE_PATHS).hasAnyAuthority(ADMIN, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.DELETE, FINANCE_WRITE_PATHS).hasAnyAuthority(ADMIN, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.PATCH, FINANCE_WRITE_PATHS).hasAnyAuthority(ADMIN, FINANCE_CONTROLLER)

                        // ---- ML model (re)training: ADMIN only ----
                        .requestMatchers(HttpMethod.POST, "/api/ml/train").hasAuthority(ADMIN)

                        // ---- Any remaining write under /api: never VIEWER (strictly read-only role) ----
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority(ADMIN, OPS_MANAGER, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority(ADMIN, OPS_MANAGER, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority(ADMIN, OPS_MANAGER, FINANCE_CONTROLLER)
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyAuthority(ADMIN, OPS_MANAGER, FINANCE_CONTROLLER)

                        // ---- Reads and everything else under /api: any authenticated role ----
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }
}
