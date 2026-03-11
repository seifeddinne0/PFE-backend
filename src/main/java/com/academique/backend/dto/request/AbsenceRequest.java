package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AbsenceRequest {
    @NotNull private LocalDate dateAbsence;
    @NotNull private Long etudiantId;
    private Long matiereId;
    private Long enseignantId;
    private String motif;
    private String statut;
}