package tn.esprit.examen.nomPrenomClasseExamen.ai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AskAssistantRequest;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantResponseDto;
import tn.esprit.examen.nomPrenomClasseExamen.ai.service.GroqAssistantService;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@Tag(name = "AI - Groq Assistant")
public class GroqAssistantController {

    private final GroqAssistantService groqAssistantService;

    @PostMapping("/ask")
    public AssistantResponseDto ask(@Valid @RequestBody AskAssistantRequest request) {
        return groqAssistantService.ask(request.getQuestion());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return groqAssistantService.assistantStatus();
    }
}
