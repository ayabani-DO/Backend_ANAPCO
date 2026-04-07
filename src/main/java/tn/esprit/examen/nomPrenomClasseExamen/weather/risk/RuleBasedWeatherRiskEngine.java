package tn.esprit.examen.nomPrenomClasseExamen.weather.risk;

import org.springframework.stereotype.Component;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedWeatherRiskEngine implements WeatherRiskRuleEngine {

    @Override
    public RiskComputationResult compute(WeatherData weatherData, SiteType siteType, SensitivityLevel sensitivityLevel) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        double wind = valueOrDefault(weatherData.getWindSpeedKmh(), 0.0);
        double precipitation = valueOrDefault(weatherData.getPrecipitationMm(), 0.0);
        double visibility = valueOrDefault(weatherData.getVisibilityKm(), 10.0);
        double humidity = valueOrDefault(weatherData.getHumidityPercent(), 0.0);

        if (wind >= 80) {
            score += 30;
            factors.add("Extreme wind speed");
        } else if (wind >= 50) {
            score += 20;
            factors.add("High wind speed");
        } else if (wind >= 30) {
            score += 10;
            factors.add("Moderate wind speed");
        }

        if (precipitation >= 30) {
            score += 25;
            factors.add("Heavy precipitation");
        } else if (precipitation >= 10) {
            score += 15;
            factors.add("Moderate precipitation");
        } else if (precipitation >= 2) {
            score += 7;
            factors.add("Light precipitation");
        }

        if (visibility <= 1) {
            score += 20;
            factors.add("Very low visibility");
        } else if (visibility <= 5) {
            score += 10;
            factors.add("Reduced visibility");
        }

        if (humidity >= 95) {
            score += 15;
            factors.add("Extreme humidity");
        } else if (humidity >= 80) {
            score += 8;
            factors.add("High humidity");
        }

        score += sensitivityWeight(sensitivityLevel);
        score += siteTypeWeight(siteType);

        score = Math.min(score, 100);
        RiskLevel level = toLevel(score);

        return RiskComputationResult.builder()
                .riskScore(score)
                .riskLevel(level)
                .factors(factors)
                .impactPersonnel(buildPersonnelImpact(level))
                .impactEquipment(buildEquipmentImpact(level))
                .impactMaintenance(buildMaintenanceImpact(level))
                .impactTransport(buildTransportImpact(level))
                .build();
    }

    private int sensitivityWeight(SensitivityLevel sensitivityLevel) {
        return switch (sensitivityLevel) {
            case LOW -> 0;
            case MEDIUM -> 7;
            case HIGH -> 14;
            case CRITICAL -> 22;
        };
    }

    private int siteTypeWeight(SiteType siteType) {
        return switch (siteType) {
            case WELL_PAD -> 14;
            case PROCESSING_FACILITY -> 12;
            case GAS_SEPARATION_UNIT -> 13;
            case STORAGE_TERMINAL -> 8;
            case TREATMENT_PLANT -> 11;
            case OTHER -> 4;
        };
    }

    private RiskLevel toLevel(int score) {
        if (score >= 75) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 50) {
            return RiskLevel.HIGH;
        }
        if (score >= 25) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String buildPersonnelImpact(RiskLevel level) {
        return switch (level) {
            case LOW -> "Minor impact on personnel activities.";
            case MEDIUM -> "Increased personnel exposure. Reinforce safety checks and PPE.";
            case HIGH -> "Significant personnel risk. Limit outdoor work and tighten safety supervision.";
            case CRITICAL -> "Severe personnel risk. Suspend exposed operations and activate emergency protocol.";
        };
    }

    private String buildEquipmentImpact(RiskLevel level) {
        return switch (level) {
            case LOW -> "Low expected impact on equipment reliability.";
            case MEDIUM -> "Potential wear acceleration on exposed equipment.";
            case HIGH -> "High probability of equipment stress and failures.";
            case CRITICAL -> "Critical equipment exposure. Immediate protection and shutdown scenarios required.";
        };
    }

    private String buildMaintenanceImpact(RiskLevel level) {
        return switch (level) {
            case LOW -> "Maintenance plan can proceed normally.";
            case MEDIUM -> "Re-prioritize preventive maintenance for weather-sensitive assets.";
            case HIGH -> "Adjust maintenance windows and postpone non-critical interventions.";
            case CRITICAL -> "Execute contingency maintenance plan and emergency inspections.";
        };
    }

    private String buildTransportImpact(RiskLevel level) {
        return switch (level) {
            case LOW -> "No major transport constraints expected.";
            case MEDIUM -> "Possible transport delays. Monitor route conditions.";
            case HIGH -> "Transport disruptions likely. Prepare alternate logistics routes.";
            case CRITICAL -> "Transport operations highly impacted. Restrict movements and activate fallback logistics.";
        };
    }

    private double valueOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }
}
