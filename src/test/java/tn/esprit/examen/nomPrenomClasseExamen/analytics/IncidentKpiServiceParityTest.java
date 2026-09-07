package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.dto.IncidentSeverityKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentKpiService;

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Parity lock for {@link IncidentKpiService#getSeverityKpi}. These exact values must stay identical
 * before and after the reverse-delegation of the severity/count primitives to
 * {@link OperationalAnalyticsService}. Only the service wiring changes between the two runs.
 */
@ExtendWith(MockitoExtension.class)
class IncidentKpiServiceParityTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private SitesRepository sitesRepository;
    @Mock
    private EquipementRepository equipementRepository;
    @Mock
    private MaintenanceRepository maintenanceRepository;

    private IncidentKpiService service;

    @BeforeEach
    void setUp() {
        OperationalAnalyticsService operational =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository);
        service = new IncidentKpiService(
                incidentRepository, sitesRepository, equipementRepository, operational);
    }

    private static Incident inc(SeverityCode sev) {
        Incident i = new Incident();
        i.setSeverityCode(sev);
        i.setEtatIncident(EtatIncident.OPEN);
        i.setDate(Date.from(LocalDate.of(2024, 6, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        return i;
    }

    @Test
    void severityKpiValuesAreStable() {
        List<Incident> incidents = List.of(inc(SeverityCode.CRITICAL), inc(SeverityCode.HIGH), inc(SeverityCode.LOW));
        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(incidents);

        IncidentSeverityKpiDto dto = service.getSeverityKpi(1L, 2024, 6);

        assertThat(dto.getTotalCount()).isEqualTo(3);
        assertThat(dto.getCriticalCount()).isEqualTo(1);
        assertThat(dto.getHighCount()).isEqualTo(1);
        assertThat(dto.getLowCount()).isEqualTo(1);
        assertThat(dto.getMediumCount()).isZero();
        assertThat(dto.getSeverityIndex()).isCloseTo(8.0 / 3.0, within(1e-9)); // (4+3+1)/3
        assertThat(dto.getCriticalRatio()).isCloseTo(100.0 / 3.0, within(1e-9));
        assertThat(dto.getRiskLevel()).isEqualTo("HIGH"); // severityIndex 2.67 > 2.5
    }
}
