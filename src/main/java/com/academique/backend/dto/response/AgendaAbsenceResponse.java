package com.academique.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaAbsenceResponse {
    private String filiereCode;
    private String niveauCode;
    private String semestreActif;
    private boolean blocked;
    private String message;
    private List<SeanceResponse> seances;
}
