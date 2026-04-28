package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank
    private String message;

    private String sessionId;

    private String language;  // "fr", "en", "ar"
}
