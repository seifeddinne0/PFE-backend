package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BatchFactureRequest {
    @NotEmpty
    private List<Long> classeIds;

    @NotNull
    private Double montant;

    private String typeFacture;
    private String typePaiement;
    private String description;
    private LocalDate dateEcheance;
}
