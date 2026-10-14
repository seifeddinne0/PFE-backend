package com.academique.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class BatchAbsenceRequest {
    @NotNull private Long seanceId;
    @NotNull private LocalDate dateAbsence;
    @NotNull private List<Long> etudiantIds;
    private String motif;
    private String justification;
    private String statut;
}
