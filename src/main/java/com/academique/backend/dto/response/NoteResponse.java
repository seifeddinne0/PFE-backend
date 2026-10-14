package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NoteResponse {
    private Long id;
    private Double valeur;
    private String typeNote;
    private String semestre;
    private String commentaire;
    private LocalDateTime createdAt;
    private Long etudiantId;
    private String etudiantNom;
    private String etudiantPrenom;
    private String etudiantMatricule;
    private Long matiereId;
    private String matiereNom;
    private Double matiereCoefficient;
    private String enseignantNom;
}