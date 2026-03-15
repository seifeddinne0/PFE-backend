package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class DemandeDocumentRequest {
    @NotNull private String typeDocument;
    private String motif;
    private List<Long> validateursIds; // pour ATTESTATION_PRESENCE
    private String nomEntrepriseStage;
    private String adresseEntreprise;
    private String nomEncadrant;
}