package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.dto.IncidentCostKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.dto.IncidentRecurrenceKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * STEP 2A — a legacy {@link Incident} with a null {@code severityCode} must not crash the
 * incident KPI aggregation. Null-severity incidents are simply excluded from the severity
 * breakdown, consistent with the analytics layer.
 */
@ExtendWith(MockitoExtension.class)
class IncidentKpiNullSeverityTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private SitesRepository sitesRepository;
    @Mock private EquipementRepository equipementRepository;
    @Mock private OperationalAnalyticsService operationalAnalytics;
    @InjectMocks private IncidentKpiService incidentKpiService;

    private static Incident incident(SeverityCode severity, Double costReal) {
        Incident i = new Incident();
        i.setSeverityCode(severity);
        i.setCostReal(costReal);
        i.setDate(new Date());
        return i;
    }

    @Test
    void costKpi_withNullSeverityIncident_doesNotThrow_andExcludesIt() {
        List<Incident> incidents = List.of(
                incident(null, 500.0),
                incident(SeverityCode.CRITICAL, 1000.0));
        when(incidentRepository.findBySitesIdSiteAndDateBetween(any(), any(), any()))
                .thenReturn(incidents);

        IncidentCostKpiDto[] dto = new IncidentCostKpiDto[1];
        assertThatCode(() -> dto[0] = incidentKpiService.getCostKpi(1L, 2024, 6, null))
                .doesNotThrowAnyException();

        assertThat(dto[0].getCostBySeverity()).containsOnlyKeys("CRITICAL");
    }

    @Test
    void recurrenceKpi_withNullSeverityIncident_doesNotThrow() {
        List<Incident> incidents = List.of(
                incident(null, null),
                incident(SeverityCode.HIGH, null));
        when(incidentRepository.findBySitesIdSiteAndDateBetween(any(), any(), any()))
                .thenReturn(incidents);
        lenient().when(operationalAnalytics.countBySeverity(any(), any())).thenReturn(0L);
        lenient().when(operationalAnalytics.severityIndex(any())).thenReturn(0.0);

        IncidentRecurrenceKpiDto[] dto = new IncidentRecurrenceKpiDto[1];
        assertThatCode(() -> dto[0] = incidentKpiService.getRecurrenceKpi(1L, 30))
                .doesNotThrowAnyException();

        assertThat(dto[0].getIncidentsBySeverity()).containsOnlyKeys("HIGH");
    }
}
