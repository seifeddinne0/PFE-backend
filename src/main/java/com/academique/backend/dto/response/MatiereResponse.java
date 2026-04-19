package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatiereResponse {
    private Long id;
    private String nom;
    private String code;
    private String description;
    private Double coefficient;
    private String semestre;
    private Long enseignantId;
}