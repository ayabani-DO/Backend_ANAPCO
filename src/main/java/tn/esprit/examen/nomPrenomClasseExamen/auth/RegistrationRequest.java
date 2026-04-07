package tn.esprit.examen.nomPrenomClasseExamen.auth;


import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder

public class RegistrationRequest {

    @NotEmpty(message = "FirstName is mandatory")
    @NotBlank(message = "FirstName is mandatory")
    private String firstName;
    @NotEmpty(message = "LastName is mandatory")
    @NotBlank(message = "LastName is mandatory")
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;
    @Email(message = "Email is not well formatted")
    @NotEmpty(message = "Email is mandatory")
    @NotBlank(message = "Email is mandatory")
    private String email;
    @NotEmpty(message = "Password is mandatory")
    @NotBlank(message = "Password is mandatory")
    @Size(min = 8, message = "password should be 8 caracters long minimum")
    private String password;
}
