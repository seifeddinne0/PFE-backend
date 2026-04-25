package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AbsenceResponse {
    private Long id;
    private LocalDate dateAbsence;
    private String statut;
    private String motif;
    private String justification;
    private String preuveJustification;
    private Boolean alerte;
    private LocalDateTime createdAt;
    private Long etudiantId;
    private String etudiantNom;
    private String etudiantPrenom;
    private String etudiantMatricule;
    private String matiereNom;
    private String enseignantNom;
    private Long totalAbsences;
    private Long seanceId;
    private String classeCode;
    private String niveauCode;
    private String filiereNom;
}