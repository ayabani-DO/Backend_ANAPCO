package tn.esprit.examen.nomPrenomClasseExamen.auth;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder

public class AuthenficationResponse {
    private String token;
}