package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class FactureRequest {
    @NotNull private Long etudiantId;
    @NotNull private Double montant;
    private String typeFacture;
    private String description;
    private LocalDate dateEcheance;
    private String statut;
}