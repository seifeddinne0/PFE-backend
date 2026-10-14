package com.academique.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoteRequest {
    @NotNull @Min(0) @Max(20)
    private Double valeur;
    @NotNull private Long etudiantId;
    @NotNull private Long matiereId;
    private Long enseignantId;
    private String typeNote;
    private String semestre;
    private String commentaire;
}