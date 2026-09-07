package tn.esprit.examen.nomPrenomClasseExamen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.ai.client.GroqClient;
import tn.esprit.examen.nomPrenomClasseExamen.ai.config.GroqProperties;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantIntent;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantLanguage;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantResponseDto;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.IntentParsingResult;
import tn.esprit.examen.nomPrenomClasseExamen.ai.ml.MlAssistantClientService;
import tn.esprit.examen.nomPrenomClasseExamen.ai.service.AssistantIntentParser;
import tn.esprit.examen.nomPrenomClasseExamen.ai.service.GroqAssistantService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the assistant now sources its numbers from the Analytics layer (same figures as the
 * dashboard), rather than the legacy KPI services.
 */
@ExtendWith(MockitoExtension.class)
class GroqAssistantRoutingTest {

    @Mock private GroqClient groqClient;
    @Mock private GroqProperties groqProperties;
    @Mock private AssistantIntentParser intentParser;
    @Mock private MlAssistantClientService mlAssistantClientService;
    @Mock private FinancialAnalyticsService financialAnalyticsService;
    @Mock private OperationalAnalyticsService operationalAnalyticsService;
    @Mock private WeatherRiskService weatherRiskService;

    private GroqAssistantService service;

    @BeforeEach
    void setUp() {
        service = new GroqAssistantService(
                groqClient, groqProperties, new ObjectMapper(), intentParser, mlAssistantClientService,
                financialAnalyticsService, operationalAnalyticsService, weatherRiskService);
        when(groqProperties.getAnswerModel()).thenReturn("model");
        when(groqClient.chatCompletion(any(), any(), any())).thenReturn("natural answer");
    }

    private IntentParsingResult parsed(AssistantIntent intent) {
        return IntentParsingResult.builder()
                .intent(intent).language(AssistantLanguage.EN).confidence(0.9)
                .siteId(1L).year(2024).month(6).build();
    }

    @Test
    void financeIntentUsesFinancialAnalytics() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .siteId(1L).year(2024).month(6).budget(1000).real(500).currency("EUR")
                .costTrend(List.of()).topExpenseCategories(List.of()).build();
        when(intentParser.parse(anyString())).thenReturn(parsed(AssistantIntent.FINANCE_KPI));
        when(financialAnalyticsService.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);

        AssistantResponseDto response = service.ask("what is my budget vs real?");

        assertThat(response.getData()).isSameAs(fin);
        assertThat(response.getIntent()).isEqualTo(AssistantIntent.FINANCE_KPI);
        verifyNoInteractions(operationalAnalyticsService);
    }

    @Test
    void incidentIntentUsesOperationalAnalytics() {
        OperationalKpiDTO op = OperationalKpiDTO.builder()
                .siteId(1L).incidentCount(3).criticalIncidentCount(1)
                .topRecurringEquipments(List.of()).build();
        when(intentParser.parse(anyString())).thenReturn(parsed(AssistantIntent.INCIDENT_RISK));
        when(operationalAnalyticsService.getOperationalKpi(1L, 2024, 6)).thenReturn(op);

        AssistantResponseDto response = service.ask("how many incidents this month?");

        assertThat(response.getData()).isSameAs(op);
        assertThat(response.getIntent()).isEqualTo(AssistantIntent.INCIDENT_RISK);
        verifyNoInteractions(financialAnalyticsService);
    }
}
