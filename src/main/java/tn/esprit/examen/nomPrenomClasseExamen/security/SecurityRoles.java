package tn.esprit.examen.nomPrenomClasseExamen.security;

import java.util.Set;

public final class SecurityRoles {

    private SecurityRoles() {
    }

    public static final String ADMIN = "ADMIN";
    public static final String OPS_MANAGER = "OPS_MANAGER";
    public static final String FINANCE_CONTROLLER = "FINANCE_CONTROLLER";
    public static final String VIEWER = "VIEWER";

    /**
     * The four fixed ANAPCO business roles — the single source of truth. Role assignment must
     * validate against this set and the role rows are seeded once on startup; the API never
     * creates new role definitions. Names are stored WITHOUT a {@code ROLE_} prefix.
     */
    public static final Set<String> ALL = Set.of(ADMIN, OPS_MANAGER, FINANCE_CONTROLLER, VIEWER);
}
