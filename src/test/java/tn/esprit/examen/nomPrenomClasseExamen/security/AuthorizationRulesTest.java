package tn.esprit.examen.nomPrenomClasseExamen.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

import tn.esprit.examen.nomPrenomClasseExamen.controllers.SiteController;
import tn.esprit.examen.nomPrenomClasseExamen.controllers.UserController;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.SiteService;
import tn.esprit.examen.nomPrenomClasseExamen.services.UserService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DB-free slice test that exercises the URL-authorization rules defined in {@link SecurityConfig}
 * against the real filter chain. The JWT filter is a pass-through here (no Authorization header);
 * the authenticated principal and its authorities are injected by {@link WithMockUser}, so these
 * tests assert exactly what each role is allowed to do.
 */
@WebMvcTest(controllers = {UserController.class, SiteController.class})
@Import({SecurityConfig.class, JwtFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AuthorizationRulesTest {

    @Autowired
    private MockMvc mvc;

    // Beans required to build the security filter chain (behaviour irrelevant to these tests).
    @MockBean
    private tn.esprit.examen.nomPrenomClasseExamen.security.JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthenticationProvider authenticationProvider;

    // Required by the main application class picked up as the slice's configuration
    // (its CommandLineRunner needs RoleRepository; @EnableJpaAuditing needs the JPA metamodel).
    @MockBean
    private RoleRepository roleRepository;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // Controller collaborators.
    @MockBean
    private UserService userService;
    @MockBean
    private SiteService siteService;

    // ---- User administration: ADMIN only ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    void admin_canListUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());
        mvc.perform(get("/users/getAll")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "OPS_MANAGER")
    void opsManager_cannotListUsers() throws Exception {
        mvc.perform(get("/users/getAll")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VIEWER")
    void viewer_cannotAssignRoles() throws Exception {
        mvc.perform(post("/users/1/assign-role").param("roleName", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymous_isUnauthorizedOnUsers() throws Exception {
        mvc.perform(get("/users/getAll")).andExpect(status().isUnauthorized());
    }

    // ---- Sites: read for everyone authenticated, write for ADMIN/OPS only ----

    @Test
    @WithMockUser(authorities = "VIEWER")
    void viewer_canReadSites() throws Exception {
        when(siteService.getAllSites()).thenReturn(List.of());
        mvc.perform(get("/api/sites/getAllSites")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "VIEWER")
    void viewer_cannotCreateSite() throws Exception {
        mvc.perform(post("/api/sites/createSite")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "FINANCE_CONTROLLER")
    void financeController_cannotCreateSite() throws Exception {
        mvc.perform(post("/api/sites/createSite")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "OPS_MANAGER")
    void opsManager_canCreateSite() throws Exception {
        when(siteService.createSite(org.mockito.ArgumentMatchers.any(Sites.class))).thenReturn(new Sites());
        mvc.perform(post("/api/sites/createSite")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void anonymous_isUnauthorizedOnSites() throws Exception {
        mvc.perform(get("/api/sites/getAllSites")).andExpect(status().isUnauthorized());
    }
}
