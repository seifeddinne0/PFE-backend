package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MatiereRequest {
    @NotBlank private String nom;
    private String code;
    private String description;
    @NotNull private Double coefficient;
    @NotNull private String semestre;
    private Long enseignantId;
    private Long niveauId;
}