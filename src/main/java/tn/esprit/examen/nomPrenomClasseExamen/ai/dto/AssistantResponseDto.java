package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AssistantResponseDto {

    private AssistantIntent intent;
    private AssistantLanguage language;
    private Double confidence;
    private List<String> suggestions;
    private Object data;
    private String naturalLanguageAnswer;
}
