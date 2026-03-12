package com.academique.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class EnseignantRequest {
    @NotBlank private String password;
    @NotBlank private String nom;
    @NotBlank private String prenom;
    @Email @NotBlank private String email;
    private String telephone;
    private LocalDate dateNaissance;
    private String adresse;
    private String specialite;
    private String grade;
    private Long userId;
}