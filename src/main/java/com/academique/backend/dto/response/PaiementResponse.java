package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PaiementResponse {
    private Long id;
    private Long factureId;
    private String factureNumero;
    private Long etudiantId;
    private String etudiantNom;
    private String etudiantPrenom;
    private String etudiantMatricule;
    private String statut;
    private String ribDestinataire;
    private String codePaiement;
    private Double montant;
    private LocalDate datePaiement;
    private String banqueEmettrice;
    private String preuvePaiement;
    private String motifRejet;
    private LocalDateTime createdAt;
}
