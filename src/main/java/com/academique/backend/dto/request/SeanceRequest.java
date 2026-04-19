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
    @NotNull private Long classeId;
    @NotNull private Long enseignantId;
    private String salle;
}
