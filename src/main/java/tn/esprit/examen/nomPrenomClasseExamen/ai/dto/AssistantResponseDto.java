package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssistantResponseDto {

    private DetectedIntent intent;
    private Object data;
    private String naturalLanguageAnswer;
}
