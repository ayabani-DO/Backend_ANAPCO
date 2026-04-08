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
public class IntentParsingResult {

    @Builder.Default
    private AssistantIntent intent = AssistantIntent.UNKNOWN;

    @Builder.Default
    private AssistantLanguage language = AssistantLanguage.FR;

    @Builder.Default
    private Double confidence = 0.0;

    private Long siteId;
    private Integer year;
    private Integer month;

    @Builder.Default
    private Integer days = 30;
}
