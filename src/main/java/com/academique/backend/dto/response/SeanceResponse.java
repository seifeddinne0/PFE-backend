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
    private String typeSeance;
    private String semestre;
    private Long niveauId;
    private String niveauCode;
    private Long creneauId;
    private String creneauLabel;

    // Matière info
    private Long matiereId;
    private String matiereNom;
    private String matiereCode;

    // Classe info
    private Long classeId;
    private String classeCode;
    private String classeNom;

    // Enseignant info
    private Long enseignantId;
    private String enseignantNom;
}
