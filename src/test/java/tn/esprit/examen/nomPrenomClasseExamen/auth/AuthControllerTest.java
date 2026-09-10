package tn.esprit.examen.nomPrenomClasseExamen.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ActivationTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.EmailAlreadyUsedException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidActivationTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidGoogleTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidResetTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ResetTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.GlobalExceptionHandler;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.JwtFilter;
import tn.esprit.examen.nomPrenomClasseExamen.security.JwtService;
import tn.esprit.examen.nomPrenomClasseExamen.security.RestAccessDeniedHandler;
import tn.esprit.examen.nomPrenomClasseExamen.security.RestAuthenticationEntryPoint;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer contract for the auth endpoints: routing, request deserialization, and the
 * exception → HTTP status mapping via {@link GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = AuthenticationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class,
        tn.esprit.examen.nomPrenomClasseExamen.config.CorsConfig.class,
        JwtFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private AuthenticationService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private AuthenticationProvider authenticationProvider;
    @MockBean private RoleRepository roleRepository;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String REGISTER_JSON = """
            {"firstName":"John","lastName":"Doe","email":"john@example.com","password":"password123"}""";
    private static final String LOGIN_JSON = """
            {"email":"john@example.com","password":"password123"}""";

    @Test
    void register_mapsToLowercasePath_deserializes_andReturns202() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
        verify(authService).register(any(RegistrationRequest.class), eq(List.of("VIEWER")));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doThrow(new EmailAlreadyUsedException("Email already in use"))
                .when(authService).register(any(), any());
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(REGISTER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already in use"));
    }

    @Test
    void register_missingRequiredFields_returns400() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_malformedBody_returns400() throws Exception {
        mvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateAccount_validToken_returns200Activated() throws Exception {
        mvc.perform(get("/auth/activate-account").param("token", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVATED"));
        verify(authService).activateaccount("123456");
    }

    @Test
    void activateAccount_invalidToken_returns400() throws Exception {
        doThrow(new InvalidActivationTokenException("Invalid activation token"))
                .when(authService).activateaccount(any());
        mvc.perform(get("/auth/activate-account").param("token", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid activation token"));
    }

    @Test
    void activateAccount_expiredToken_returns410() throws Exception {
        doThrow(new ActivationTokenExpiredException("Activation token has expired. Please register again to receive a new activation email."))
                .when(authService).activateaccount(any());
        mvc.perform(get("/auth/activate-account").param("token", "old"))
                .andExpect(status().isGone());
    }

    @Test
    void activateAccount_missingTokenParam_returns400() throws Exception {
        mvc.perform(get("/auth/activate-account"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required parameter: token"));
    }

    @Test
    void authenticate_isPublicMethod_mapsAndReturnsToken() throws Exception {
        when(authService.authenficate(any()))
                .thenReturn(AuthenficationResponse.builder().token("jwt-abc").build());
        mvc.perform(post("/auth/authenticate").contentType(APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-abc"));
    }

    @Test
    void authenticate_badCredentials_returns401() throws Exception {
        when(authService.authenficate(any())).thenThrow(new BadCredentialsException("bad"));
        mvc.perform(post("/auth/authenticate").contentType(APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void authenticate_disabledAccount_returns401WithActivationHint() throws Exception {
        when(authService.authenficate(any())).thenThrow(new DisabledException("disabled"));
        mvc.perform(post("/auth/authenticate").contentType(APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not activated")));
    }

    @Test
    void google_invalidToken_returns401() throws Exception {
        when(authService.authenticateWithGoogle(any()))
                .thenThrow(new InvalidGoogleTokenException("Invalid Google token"));
        mvc.perform(post("/auth/google").param("googleToken", "bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid Google token"));
    }

    @Test
    void google_missingTokenParam_returns400() throws Exception {
        mvc.perform(post("/auth/google"))
                .andExpect(status().isBadRequest());
    }

    // ---- Part 2A: forgot-password (no account enumeration) ----

    private static final String UPDATE_PW_JSON = """
            {"email":"john@example.com","currentPassword":"oldpassword","newPassword":"newpassword"}""";

    @Test
    void forgotPassword_existingEmail_returnsGeneric200() throws Exception {
        mvc.perform(post("/auth/forgot-password").param("email", "john@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("If an account exists")));
    }

    @Test
    void forgotPassword_unknownEmail_returnsSameGeneric200() throws Exception {
        // Service is a no-op for unknown emails; the controller cannot and must not distinguish.
        mvc.perform(post("/auth/forgot-password").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("If an account exists")));
        verify(authService).forgotPassword("nobody@example.com");
    }

    // ---- Part 2A: reset-password (typed exceptions, length) ----

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        doThrow(new InvalidResetTokenException("Invalid or unknown reset token"))
                .when(authService).resetPassword(any(), any());
        mvc.perform(post("/auth/reset-password").param("token", "nope").param("newPassword", "newpassword"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or unknown reset token"));
    }

    @Test
    void resetPassword_expiredToken_returns410() throws Exception {
        doThrow(new ResetTokenExpiredException("Reset token has expired. Please request a new password-reset email."))
                .when(authService).resetPassword(any(), any());
        mvc.perform(post("/auth/reset-password").param("token", "old").param("newPassword", "newpassword"))
                .andExpect(status().isGone());
    }

    @Test
    void resetPassword_shortNewPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("newPassword: password should be 8 characters long minimum"))
                .when(authService).resetPassword(any(), any());
        mvc.perform(post("/auth/reset-password").param("token", "t").param("newPassword", "short"))
                .andExpect(status().isBadRequest());
    }

    // ---- Part 2A: update-password (ownership from principal) ----

    @Test
    void updatePassword_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/auth/update-password").contentType(APPLICATION_JSON).content(UPDATE_PW_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void updatePassword_authenticated_usesPrincipalNotBodyEmail() throws Exception {
        String bodyWithAttackerEmail = """
                {"email":"victim@example.com","currentPassword":"oldpassword","newPassword":"newpassword"}""";
        mvc.perform(post("/auth/update-password").contentType(APPLICATION_JSON).content(bodyWithAttackerEmail))
                .andExpect(status().isOk());
        // The service must be called with the JWT principal, never the body's email.
        verify(authService).updatePassword(eq("john@example.com"), any(ResetPasswordDto.class));
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void updatePassword_shortNewPassword_returns400() throws Exception {
        String shortPw = """
                {"email":"john@example.com","currentPassword":"oldpassword","newPassword":"short"}""";
        mvc.perform(post("/auth/update-password").contentType(APPLICATION_JSON).content(shortPw))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void updatePassword_ownershipMismatch_returns403() throws Exception {
        doThrow(new AccessDeniedException("You can only change your own password"))
                .when(authService).updatePassword(eq("john@example.com"), any(ResetPasswordDto.class));
        mvc.perform(post("/auth/update-password").contentType(APPLICATION_JSON).content(UPDATE_PW_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "john@example.com")
    void updatePassword_wrongCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(authService).updatePassword(eq("john@example.com"), any(ResetPasswordDto.class));
        mvc.perform(post("/auth/update-password").contentType(APPLICATION_JSON).content(UPDATE_PW_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    // ---- Part 2B: token-purpose separation surfaces as 400 ----

    @Test
    void resetPassword_wrongTokenType_returns400() throws Exception {
        doThrow(new InvalidResetTokenException("This link is not a password-reset link"))
                .when(authService).resetPassword(any(), any());
        mvc.perform(post("/auth/reset-password").param("token", "123456").param("newPassword", "newpassword"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This link is not a password-reset link"));
    }

    @Test
    void activateAccount_wrongTokenType_returns400() throws Exception {
        doThrow(new InvalidActivationTokenException("This link is not an account-activation link"))
                .when(authService).activateaccount(any());
        mvc.perform(get("/auth/activate-account").param("token", "reset-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This link is not an account-activation link"));
    }
}
