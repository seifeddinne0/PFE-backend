package com.academique.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeanceResponse {
    private Long id;
    private String jourSemaine;
    private LocalTime heureDebut;
    private LocalTime heureFin;
    private String salle;

    // Matière info
    private Long matiereId;
    private String matiereNom;
    private String matiereCode;
    private String semestre;

    // Classe info
    private Long classeId;
    private String classeCode;
    private String classeNom;

    // Enseignant info
    private Long enseignantId;
    private String enseignantNom;
}
