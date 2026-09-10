package tn.esprit.examen.nomPrenomClasseExamen.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tn.esprit.examen.nomPrenomClasseExamen.auth.AuthenticationController;
import tn.esprit.examen.nomPrenomClasseExamen.auth.AuthenticationService;
import tn.esprit.examen.nomPrenomClasseExamen.config.CorsConfig;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CORS layer is now a single {@link CorsConfig} {@code CorsConfigurationSource} consumed by
 * Spring Security — one {@code Access-Control-Allow-Origin} header, configurable origins, no "*".
 */
@WebMvcTest(controllers = AuthenticationController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CorsSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private AuthenticationService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private AuthenticationProvider authenticationProvider;
    @MockBean private RoleRepository roleRepository;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mvc.perform(options("/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().stringValues(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("POST"))));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mvc.perform(options("/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exactlyOneAllowOriginHeader() throws Exception {
        MvcResult result = mvc.perform(options("/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).hasSize(1);
    }
}
