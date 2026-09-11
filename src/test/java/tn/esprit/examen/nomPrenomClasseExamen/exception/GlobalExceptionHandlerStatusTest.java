package tn.esprit.examen.nomPrenomClasseExamen.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * STEP 2A — verifies the HTTP status mapping added for the operational domain:
 * missing resource → 404, invalid domain input → 400, dependency/integrity conflict → 409.
 * Pure unit test: the advice methods are invoked directly, no Spring context.
 */
class GlobalExceptionHandlerStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFound_mapsTo404() {
        assertThat(handler.handleResourceNotFound(new ResourceNotFoundException("Site not found: 1"))
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void dependencyExists_mapsTo409() {
        assertThat(handler.handleDependencyExists(new DependencyExistsException("still has equipment"))
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void dataIntegrityViolation_mapsTo409_notRaw500() {
        assertThat(handler.handleDataIntegrity(new DataIntegrityViolationException("FK constraint"))
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidDomainInput_mapsTo400() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("currencyCode is required"))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void businessConflict_mapsTo409() {
        assertThat(handler.handleIllegalState(new IllegalStateException("conflict"))
                .getStatusCode().value()).isEqualTo(409);
    }
}
