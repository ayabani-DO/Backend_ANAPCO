package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AskAssistantRequest {

    @NotBlank
    private String question;
}
