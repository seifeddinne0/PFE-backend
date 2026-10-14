package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalTime;

@Data
public class SeanceRequest {
    @NotBlank private String jourSemaine;
    @NotNull private LocalTime heureDebut;
    @NotNull private LocalTime heureFin;
    @NotNull private Long matiereId;
    private Long classeId;
    private Long enseignantId;
    @NotBlank private String typeSeance;
    @NotBlank private String semestre;
    @NotNull private Long niveauId;
    @NotNull private Long creneauId;
    private String salle;
}
