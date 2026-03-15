package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class EnseignantResponse {
    private Long id;
    private String matricule;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private LocalDate dateNaissance;
    private String adresse;
    private String specialite;
    private String grade;
    private String statut;
    private LocalDateTime createdAt;
    private String userEmail;
    private String photo;
}