package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignEnseignantRequest {
    @NotNull
    private Long matiereId;
    
    @NotBlank
    private String semestre;
    
    @NotNull
    private Long enseignantId;
}
