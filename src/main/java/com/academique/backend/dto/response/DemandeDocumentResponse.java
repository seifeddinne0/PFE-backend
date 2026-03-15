package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DemandeDocumentResponse {
    private Long id;
    private String typeDocument;
    private String statut;
    private String motif;
    private String commentaireAdmin;
    private String nomEntrepriseStage;
    private String adresseEntreprise;
    private String nomEncadrant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long etudiantId;
    private String etudiantNom;
    private String etudiantPrenom;
    private String etudiantMatricule;
    private String etudiantEmail;
    private List<ValidateurInfo> validateurs;
    private int nombreValidations;
    private boolean pretPourEnvoi;

    @Data
    @Builder
    public static class ValidateurInfo {
        private Long enseignantId;
        private String nom;
        private String prenom;
        private String statut;
        private String commentaire;
    }
}