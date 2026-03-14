package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FactureResponse {
    private Long id;
    private String numero;
    private Double montant;
    private String statut;
    private String typeFacture;
    private String description;
    private LocalDate dateEcheance;
    private LocalDate datePaiement;
    private LocalDateTime createdAt;
    private Long etudiantId;
    private String etudiantNom;
    private String etudiantPrenom;
    private String etudiantMatricule;
}