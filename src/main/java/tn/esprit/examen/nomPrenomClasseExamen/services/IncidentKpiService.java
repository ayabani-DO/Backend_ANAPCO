package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.dto.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentKpiService {

    private final IncidentRepository incidentRepository;
    private final SitesRepository sitesRepository;
    private final EquipementRepository equipementRepository;

    // PHASE 1: CORE KPI METHODS =================================================

    public IncidentSeverityKpiDto getSeverityKpi(Long siteId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate, endDate);

        return IncidentSeverityKpiDto.builder()
            .siteId(siteId)
            .year(year)
            .month(month)
            .lowCount(countBySeverity(incidents, SeverityCode.LOW))
            .mediumCount(countBySeverity(incidents, SeverityCode.MEDIUM))
            .highCount(countBySeverity(incidents, SeverityCode.HIGH))
            .criticalCount(countBySeverity(incidents, SeverityCode.CRITICAL))
            .totalCount((long) incidents.size())
            .lowPercentage(calculatePercentage(incidents, SeverityCode.LOW))
            .mediumPercentage(calculatePercentage(incidents, SeverityCode.MEDIUM))
            .highPercentage(calculatePercentage(incidents, SeverityCode.HIGH))
            .criticalPercentage(calculatePercentage(incidents, SeverityCode.CRITICAL))
            .severityIndex(calculateSeverityIndex(incidents))
            .criticalRatio(calculateCriticalRatio(incidents))
            .riskLevel(determineRiskLevel(calculateSeverityIndex(incidents)))
            .build();
    }

    public IncidentLifecycleKpiDto getLifecycleKpi(Long siteId, Integer year, Integer month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate, endDate);

        List<Incident> closedIncidents = incidents.stream()
            .filter(i -> i.getEtatIncident() == EtatIncident.CLOSED && i.getClosedDate() != null)
            .collect(Collectors.toList());

        return IncidentLifecycleKpiDto.builder()
            .siteId(siteId)
            .year(year)
            .month(month)
            .openCount(countByStatus(incidents, EtatIncident.OPEN))
            .inProgressCount(countByStatus(incidents, EtatIncident.IN_PROGRESS))
            .closedCount((long) closedIncidents.size())
            .totalCount((long) incidents.size())
            .closureRate(calculateClosureRate(incidents))
            .avgResolutionDays(calculateAvgResolutionDays(closedIncidents))
            .avgResolutionHours(calculateAvgResolutionHours(closedIncidents))
            .longestResolutionDays(calculateLongestResolutionDays(closedIncidents))
            .shortestResolutionDays(calculateShortestResolutionDays(closedIncidents))
            .performanceLevel(determinePerformanceLevel(calculateClosureRate(incidents)))
            .openIncidentRatio(calculateOpenRatio(incidents))
            .inProgressRatio(calculateInProgressRatio(incidents))
            .build();
    }

    public IncidentCostKpiDto getCostKpi(Long siteId, Integer year, Integer month, String targetCurrency) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate, endDate);

        List<Incident> incidentsWithCosts = incidents.stream()
            .filter(i -> i.getCostEstimated() != null || i.getCostReal() != null)
            .collect(Collectors.toList());

        return IncidentCostKpiDto.builder()
            .siteId(siteId)
            .year(year)
            .month(month)
            .targetCurrency(targetCurrency != null ? targetCurrency : "EUR")
            .totalEstimatedCost(calculateTotalEstimatedCost(incidentsWithCosts))
            .totalRealCost(calculateTotalRealCost(incidentsWithCosts))
            .totalCostVariance(calculateCostVariance(incidentsWithCosts))
            .totalCostVariancePercent(calculateCostVariancePercent(incidentsWithCosts))
            .avgCostPerIncident(calculateAvgCostPerIncident(incidentsWithCosts))
            .avgEstimatedCostPerIncident(calculateAvgEstimatedCost(incidentsWithCosts))
            .avgRealCostPerIncident(calculateAvgRealCost(incidentsWithCosts))
            .costBySeverity(calculateCostBySeverity(incidentsWithCosts))
            .costByEquipmentCategory(calculateCostByEquipmentCategory(incidentsWithCosts))
            .costPerformanceLevel(determineCostPerformanceLevel(incidentsWithCosts))
            .costOverrunRatio(calculateCostOverrunRatio(incidentsWithCosts))
            .totalIncidentsWithCosts((long) incidentsWithCosts.size())
            .build();
    }

    public IncidentRecurrenceKpiDto getRecurrenceKpi(Long siteId, Integer days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate, endDate);

        return IncidentRecurrenceKpiDto.builder()
            .siteId(siteId)
            .analysisDays(days)
            .totalIncidents((long) incidents.size())
            .repeatedIncidents(calculateRepeatedIncidents(incidents))
            .recurrenceRate(calculateRecurrenceRate(incidents))
            .incidentsByEquipment(calculateIncidentsByEquipment(incidents))
            .highRiskEquipment(calculateHighRiskEquipment(incidents))
            .incidentsBySite(calculateIncidentsBySite(incidents))
            .highRiskSites(calculateHighRiskSites(incidents))
            .incidentsBySeverity(calculateIncidentsBySeverity(incidents))
            .criticalIncidentsLastPeriod(countBySeverity(incidents, SeverityCode.CRITICAL))
            .highIncidentsLastPeriod(countBySeverity(incidents, SeverityCode.HIGH))
            .overallRiskLevel(determineOverallRiskLevel(incidents))
            .riskFactors(calculateRiskFactors(incidents))
            .recommendations(generateRecommendations(incidents))
            .incidentTrend(calculateIncidentTrend(siteId, days))
            .trendDirection(determineTrendDirection(siteId, days))
            .build();
    }

    // PHASE 2: RISK SCORING METHODS =============================================

    public IncidentRiskScoreDto calculateSiteRiskScore(Long siteId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate30 = endDate.minusDays(30);
        LocalDate startDate90 = endDate.minusDays(90);
        
        List<Incident> incidentsLast30Days = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate30, endDate);
        List<Incident> incidentsLast90Days = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, startDate90, endDate);

        Sites site = sitesRepository.findById(siteId)
            .orElseThrow(() -> new RuntimeException("Site not found"));

        // Risk scoring logic
        Integer riskScore = 0;
        List<String> riskFactors = new ArrayList<>();
        List<String> positiveFactors = new ArrayList<>();

        // Factor 1: Incident frequency (30 points)
        if (incidentsLast30Days.size() >= 10) {
            riskScore += 30;
            riskFactors.add("High incident frequency: " + incidentsLast30Days.size() + " incidents in 30 days");
        } else if (incidentsLast30Days.size() >= 5) {
            riskScore += 20;
            riskFactors.add("Moderate incident frequency: " + incidentsLast30Days.size() + " incidents in 30 days");
        } else {
            positiveFactors.add("Low incident frequency: " + incidentsLast30Days.size() + " incidents in 30 days");
        }

        // Factor 2: Critical incidents (25 points)
        long criticalCount = incidentsLast90Days.stream()
            .filter(i -> i.getSeverityCode() == SeverityCode.CRITICAL)
            .count();
        if (criticalCount >= 2) {
            riskScore += 25;
            riskFactors.add("Multiple critical incidents: " + criticalCount + " in 90 days");
        } else if (criticalCount >= 1) {
            riskScore += 15;
            riskFactors.add("Critical incident detected: " + criticalCount + " in 90 days");
        } else {
            positiveFactors.add("No critical incidents in last 90 days");
        }

        // Factor 3: High incidents (15 points)
        long highCount = incidentsLast30Days.stream()
            .filter(i -> i.getSeverityCode() == SeverityCode.HIGH)
            .count();
        if (highCount >= 3) {
            riskScore += 15;
            riskFactors.add("Multiple high severity incidents: " + highCount + " in 30 days");
        } else if (highCount >= 1) {
            riskScore += 8;
            riskFactors.add("High severity incidents: " + highCount + " in 30 days");
        }

        // Factor 4: Cost overruns (20 points)
        double avgCostOverrun = calculateAvgCostOverrunPercent(incidentsLast90Days);
        if (avgCostOverrun > 30) {
            riskScore += 20;
            riskFactors.add("Significant cost overruns: " + String.format("%.1f%%", avgCostOverrun));
        } else if (avgCostOverrun > 15) {
            riskScore += 10;
            riskFactors.add("Moderate cost overruns: " + String.format("%.1f%%", avgCostOverrun));
        } else {
            positiveFactors.add("Cost estimates are accurate");
        }

        // Factor 5: Open incidents (10 points)
        long openCount = incidentsLast30Days.stream()
            .filter(i -> i.getEtatIncident() == EtatIncident.OPEN)
            .count();
        if (openCount >= 3) {
            riskScore += 10;
            riskFactors.add("Multiple open incidents: " + openCount);
        }

        return IncidentRiskScoreDto.builder()
            .siteId(siteId)
            .targetName(site.getNom())
            .targetType("SITE")
            .riskScore(Math.min(riskScore, 100))
            .riskLevel(determineRiskLevelFromScore(riskScore))
            .riskCategory("OPERATIONAL")
            .riskFactors(riskFactors)
            .positiveFactors(positiveFactors)
            .incidentsLast30Days(incidentsLast30Days.size())
            .criticalIncidentsLast90Days((int) criticalCount)
            .highIncidentsLast30Days((int) highCount)
            .avgCostOverrunPercent(avgCostOverrun)
            .recommendation(generateSiteRecommendation(riskScore, riskFactors))
            .urgencyLevel(determineUrgencyLevel(riskScore))
            .suggestedActions(generateSiteActions(riskScore, riskFactors))
            .analysisDate(LocalDate.now())
            .dataPointsAnalyzed(incidentsLast90Days.size())
            .confidenceScore(calculateConfidenceScore(incidentsLast90Days.size()))
            .build();
    }

    public IncidentRiskScoreDto calculateEquipmentRiskScore(Long equipmentId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate30 = endDate.minusDays(30);
        LocalDate startDate90 = endDate.minusDays(90);
        
        List<Incident> incidentsLast30Days = incidentRepository.findByEquipementIdEquipementAndDateBetween(
            equipmentId, startDate30, endDate);
        List<Incident> incidentsLast90Days = incidentRepository.findByEquipementIdEquipementAndDateBetween(
            equipmentId, startDate90, endDate);

        Equipement equipment = equipementRepository.findById(equipmentId)
            .orElseThrow(() -> new RuntimeException("Equipment not found"));

        // Risk scoring logic for equipment
        Integer riskScore = 0;
        List<String> riskFactors = new ArrayList<>();
        List<String> positiveFactors = new ArrayList<>();

        // Factor 1: Incident frequency (30 points)
        if (incidentsLast30Days.size() >= 3) {
            riskScore += 30;
            riskFactors.add("High incident frequency: " + incidentsLast30Days.size() + " incidents in 30 days");
        } else if (incidentsLast30Days.size() >= 1) {
            riskScore += 15;
            riskFactors.add("Recent incidents: " + incidentsLast30Days.size() + " in 30 days");
        } else {
            positiveFactors.add("No recent incidents");
        }

        // Factor 2: Equipment status (25 points)
        if (equipment.getStatusEquipement() == StatusEquipement.OUTOFSERVICE) {
            riskScore += 25;
            riskFactors.add("Equipment currently out of service");
        } else if (equipment.getStatusEquipement() == StatusEquipement.SUSPENDUE) {
            riskScore += 15;
            riskFactors.add("Equipment currently suspended");
        } else {
            positiveFactors.add("Equipment operational");
        }

        // Factor 3: Critical incidents (25 points)
        long criticalCount = incidentsLast90Days.stream()
            .filter(i -> i.getSeverityCode() == SeverityCode.CRITICAL)
            .count();
        if (criticalCount >= 1) {
            riskScore += 25;
            riskFactors.add("Critical incident history: " + criticalCount + " in 90 days");
        }

        // Factor 4: Cost overruns (20 points)
        double avgCostOverrun = calculateAvgCostOverrunPercent(incidentsLast90Days);
        if (avgCostOverrun > 25) {
            riskScore += 20;
            riskFactors.add("High repair cost overruns: " + String.format("%.1f%%", avgCostOverrun));
        }

        return IncidentRiskScoreDto.builder()
            .equipmentId(equipmentId)
            .targetName(equipment.getNomEquipement())
            .targetType("EQUIPMENT")
            .riskScore(Math.min(riskScore, 100))
            .riskLevel(determineRiskLevelFromScore(riskScore))
            .riskCategory("OPERATIONAL")
            .riskFactors(riskFactors)
            .positiveFactors(positiveFactors)
            .incidentsLast30Days(incidentsLast30Days.size())
            .criticalIncidentsLast90Days((int) criticalCount)
            .avgCostOverrunPercent(avgCostOverrun)
            .equipmentOutOfService(equipment.getStatusEquipement() == StatusEquipement.OUTOFSERVICE)
            .recommendation(generateEquipmentRecommendation(riskScore, riskFactors))
            .urgencyLevel(determineUrgencyLevel(riskScore))
            .suggestedActions(generateEquipmentActions(riskScore, riskFactors))
            .analysisDate(LocalDate.now())
            .dataPointsAnalyzed(incidentsLast90Days.size())
            .confidenceScore(calculateConfidenceScore(incidentsLast90Days.size()))
            .build();
    }

    // HELPER METHODS ===========================================================

    private Long countBySeverity(List<Incident> incidents, SeverityCode severity) {
        return incidents.stream().filter(i -> i.getSeverityCode() == severity).count();
    }

    private Long countByStatus(List<Incident> incidents, EtatIncident status) {
        return incidents.stream().filter(i -> i.getEtatIncident() == status).count();
    }

    private Double calculatePercentage(List<Incident> incidents, SeverityCode severity) {
        if (incidents.isEmpty()) return 0.0;
        return (countBySeverity(incidents, severity) * 100.0) / incidents.size();
    }

    private Double calculateSeverityIndex(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        double totalWeight = incidents.stream()
            .mapToDouble(i -> i.getSeverityCode().getWeight())
            .sum();
        return totalWeight / incidents.size();
    }

    private Double calculateCriticalRatio(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return (countBySeverity(incidents, SeverityCode.CRITICAL) * 100.0) / incidents.size();
    }

    private String determineRiskLevel(Double severityIndex) {
        if (severityIndex <= 1.5) return "LOW";
        if (severityIndex <= 2.5) return "MEDIUM";
        return "HIGH";
    }

    private Double calculateClosureRate(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return (countByStatus(incidents, EtatIncident.CLOSED) * 100.0) / incidents.size();
    }

    private Double calculateAvgResolutionDays(List<Incident> closedIncidents) {
        if (closedIncidents.isEmpty()) return 0.0;
        return closedIncidents.stream()
            .mapToLong(i -> calculateDaysBetween(i.getDate(), i.getClosedDate()))
            .average()
            .orElse(0.0);
    }

    private Double calculateAvgResolutionHours(List<Incident> closedIncidents) {
        if (closedIncidents.isEmpty()) return 0.0;
        return calculateAvgResolutionDays(closedIncidents) * 24;
    }

    private Long calculateLongestResolutionDays(List<Incident> closedIncidents) {
        if (closedIncidents.isEmpty()) return 0L;
        return closedIncidents.stream()
            .mapToLong(i -> (long) calculateDaysBetween(i.getDate(), i.getClosedDate()))
            .max()
            .orElse(0L);
    }

    private Long calculateShortestResolutionDays(List<Incident> closedIncidents) {
        if (closedIncidents.isEmpty()) return 0L;
        return closedIncidents.stream()
            .mapToLong(i -> (long) calculateDaysBetween(i.getDate(), i.getClosedDate()))
            .min()
            .orElse(0L);
    }

    private long calculateDaysBetween(Date start, Date end) {
        if (start == null || end == null) return 0L;
        long diffInMillies = end.getTime() - start.getTime();
        return diffInMillies / (1000L * 60 * 60 * 24);
    }

    private String determinePerformanceLevel(Double closureRate) {
        if (closureRate >= 90) return "EXCELLENT";
        if (closureRate >= 70) return "GOOD";
        return "POOR";
    }

    private Double calculateOpenRatio(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return (countByStatus(incidents, EtatIncident.OPEN) * 100.0) / incidents.size();
    }

    private Double calculateInProgressRatio(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return (countByStatus(incidents, EtatIncident.IN_PROGRESS) * 100.0) / incidents.size();
    }

    private Double calculateTotalEstimatedCost(List<Incident> incidents) {
        return incidents.stream()
            .mapToDouble(i -> i.getCostEstimated() != null ? i.getCostEstimated() : 0.0)
            .sum();
    }

    private Double calculateTotalRealCost(List<Incident> incidents) {
        return incidents.stream()
            .mapToDouble(i -> i.getCostReal() != null ? i.getCostReal() : 0.0)
            .sum();
    }

    private Double calculateCostVariance(List<Incident> incidents) {
        return calculateTotalRealCost(incidents) - calculateTotalEstimatedCost(incidents);
    }

    private Double calculateCostVariancePercent(List<Incident> incidents) {
        double estimated = calculateTotalEstimatedCost(incidents);
        if (estimated == 0) return 0.0;
        return (calculateCostVariance(incidents) / estimated) * 100;
    }

    private Double calculateAvgCostPerIncident(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return calculateTotalRealCost(incidents) / incidents.size();
    }

    private Double calculateAvgEstimatedCost(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return calculateTotalEstimatedCost(incidents) / incidents.size();
    }

    private Double calculateAvgRealCost(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return calculateTotalRealCost(incidents) / incidents.size();
    }

    private Map<String, Double> calculateCostBySeverity(List<Incident> incidents) {
        return incidents.stream()
            .filter(i -> i.getCostReal() != null)
            .collect(Collectors.groupingBy(
                i -> i.getSeverityCode().toString(),
                Collectors.summingDouble(Incident::getCostReal)
            ));
    }

    private Map<String, Double> calculateCostByEquipmentCategory(List<Incident> incidents) {
        return incidents.stream()
            .filter(i -> i.getCostReal() != null && i.getEquipement() != null && i.getEquipement().getCategorie() != null)
            .collect(Collectors.groupingBy(
                i -> i.getEquipement().getCategorie().getNomEquiepment(),
                Collectors.summingDouble(Incident::getCostReal)
            ));
    }

    private String determineCostPerformanceLevel(List<Incident> incidents) {
        double variance = calculateCostVariancePercent(incidents);
        if (variance <= 5) return "ON_BUDGET";
        if (variance > 0) return "OVER_BUDGET";
        return "UNDER_BUDGET";
    }

    private Double calculateCostOverrunRatio(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        long overruns = incidents.stream()
            .filter(i -> i.getCostEstimated() != null && i.getCostReal() != null && i.getCostReal() > i.getCostEstimated())
            .count();
        return (overruns * 100.0) / incidents.size();
    }

    private Long calculateRepeatedIncidents(List<Incident> incidents) {
        Map<Long, Long> equipmentIncidentCount = incidents.stream()
            .filter(i -> i.getEquipement() != null)
            .collect(Collectors.groupingBy(
                i -> i.getEquipement().getIdEquipement(),
                Collectors.counting()
            ));
        return equipmentIncidentCount.values().stream()
            .filter(count -> count > 1)
            .count();
    }

    private Double calculateRecurrenceRate(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        return (calculateRepeatedIncidents(incidents) * 100.0) / incidents.size();
    }

    private Map<String, Long> calculateIncidentsByEquipment(List<Incident> incidents) {
        return incidents.stream()
            .filter(i -> i.getEquipement() != null)
            .collect(Collectors.groupingBy(
                i -> i.getEquipement().getNomEquipement(),
                Collectors.counting()
            ));
    }

    private List<String> calculateHighRiskEquipment(List<Incident> incidents) {
        Map<String, Long> equipmentCounts = calculateIncidentsByEquipment(incidents);
        return equipmentCounts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private Map<String, Long> calculateIncidentsBySite(List<Incident> incidents) {
        return incidents.stream()
            .filter(i -> i.getSites() != null)
            .collect(Collectors.groupingBy(
                i -> i.getSites().getCodeRef(),
                Collectors.counting()
            ));
    }

    private List<String> calculateHighRiskSites(List<Incident> incidents) {
        Map<String, Long> siteCounts = calculateIncidentsBySite(incidents);
        return siteCounts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private Map<String, Long> calculateIncidentsBySeverity(List<Incident> incidents) {
        return incidents.stream()
            .collect(Collectors.groupingBy(
                i -> i.getSeverityCode().toString(),
                Collectors.counting()
            ));
    }

    private String determineOverallRiskLevel(List<Incident> incidents) {
        double severityIndex = calculateSeverityIndex(incidents);
        return determineRiskLevel(severityIndex);
    }

    private List<String> calculateRiskFactors(List<Incident> incidents) {
        List<String> factors = new ArrayList<>();
        
        if (incidents.size() >= 10) {
            factors.add("High incident volume");
        }
        
        long criticalCount = countBySeverity(incidents, SeverityCode.CRITICAL);
        if (criticalCount > 0) {
            factors.add("Critical incidents present");
        }
        
        double recurrenceRate = calculateRecurrenceRate(incidents);
        if (recurrenceRate > 30) {
            factors.add("High recurrence rate");
        }
        
        return factors;
    }

    private List<String> generateRecommendations(List<Incident> incidents) {
        List<String> recommendations = new ArrayList<>();
        
        long criticalCount = countBySeverity(incidents, SeverityCode.CRITICAL);
        if (criticalCount > 0) {
            recommendations.add("Prioritize critical incident resolution");
        }
        
        double recurrenceRate = calculateRecurrenceRate(incidents);
        if (recurrenceRate > 30) {
            recommendations.add("Investigate recurring equipment issues");
        }
        
        if (incidents.size() >= 10) {
            recommendations.add("Increase preventive maintenance frequency");
        }
        
        return recommendations;
    }

    private Double calculateIncidentTrend(Long siteId, Integer days) {
        // Simple trend calculation: compare last period with previous period
        LocalDate currentEnd = LocalDate.now();
        LocalDate currentStart = currentEnd.minusDays(days);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days);
        
        List<Incident> currentPeriod = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, currentStart, currentEnd);
        List<Incident> previousPeriod = incidentRepository.findBySitesIdSiteAndDateBetween(
            siteId, previousStart, previousEnd);
        
        if (previousPeriod.isEmpty()) return 0.0;
        return ((currentPeriod.size() - previousPeriod.size()) * 100.0) / previousPeriod.size();
    }

    private String determineTrendDirection(Long siteId, Integer days) {
        double trend = calculateIncidentTrend(siteId, days);
        if (trend > 10) return "INCREASING";
        if (trend < -10) return "DECREASING";
        return "STABLE";
    }

    private double calculateAvgCostOverrunPercent(List<Incident> incidents) {
        List<Incident> withBothCosts = incidents.stream()
            .filter(i -> i.getCostEstimated() != null && i.getCostReal() != null)
            .collect(Collectors.toList());
        
        if (withBothCosts.isEmpty()) return 0.0;
        
        double totalOverrun = withBothCosts.stream()
            .mapToDouble(i -> ((i.getCostReal() - i.getCostEstimated()) / i.getCostEstimated()) * 100)
            .sum();
        
        return totalOverrun / withBothCosts.size();
    }

    private String determineRiskLevelFromScore(Integer score) {
        if (score <= 29) return "LOW";
        if (score <= 59) return "MEDIUM";
        return "HIGH";
    }

    private String generateSiteRecommendation(Integer score, List<String> riskFactors) {
        if (score >= 60) {
            return "Immediate action required: Conduct comprehensive site safety audit";
        } else if (score >= 30) {
            return "Schedule detailed inspection within 7 days";
        }
        return "Continue normal monitoring with monthly review";
    }

    private String generateEquipmentRecommendation(Integer score, List<String> riskFactors) {
        if (score >= 60) {
            return "Schedule immediate preventive maintenance or replacement";
        } else if (score >= 30) {
            return "Schedule inspection within 7 days";
        }
        return "Continue normal operation with routine monitoring";
    }

    private String determineUrgencyLevel(Integer score) {
        if (score >= 60) return "IMMEDIATE";
        if (score >= 30) return "WITHIN_7_DAYS";
        return "WITHIN_30_DAYS";
    }

    private List<String> generateSiteActions(Integer score, List<String> riskFactors) {
        List<String> actions = new ArrayList<>();
        
        if (score >= 60) {
            actions.add("Conduct emergency safety assessment");
            actions.add("Increase supervision frequency");
            actions.add("Review operating procedures");
        } else if (score >= 30) {
            actions.add("Schedule preventive maintenance");
            actions.add("Review recent incident patterns");
        }
        
        return actions;
    }

    private List<String> generateEquipmentActions(Integer score, List<String> riskFactors) {
        List<String> actions = new ArrayList<>();
        
        if (score >= 60) {
            actions.add("Immediate equipment inspection");
            actions.add("Consider temporary shutdown");
            actions.add("Order replacement parts");
        } else if (score >= 30) {
            actions.add("Schedule preventive maintenance");
            actions.add("Check related equipment");
        }
        
        return actions;
    }

    private Double calculateConfidenceScore(int dataPoints) {
        if (dataPoints >= 20) return 0.9;
        if (dataPoints >= 10) return 0.8;
        if (dataPoints >= 5) return 0.7;
        return 0.5;
    }
}
