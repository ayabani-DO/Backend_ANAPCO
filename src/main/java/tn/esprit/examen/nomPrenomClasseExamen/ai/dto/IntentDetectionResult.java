package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentDetectionResult {

    @Builder.Default
    private DetectedIntent intent = DetectedIntent.UNKNOWN;

    private Long siteId;
    private Integer year;
    private Integer month;

    @Builder.Default
    private Integer days = 30;
}
