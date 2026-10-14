package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "seances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourSemaine jourSemaine;

    @Column(nullable = false)
    private LocalTime heureDebut;

    @Column(nullable = false)
    private LocalTime heureFin;

    @ManyToOne
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    @ManyToOne
    @JoinColumn(name = "classe_id")
    private Classe classe;

    @ManyToOne
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_seance", nullable = false)
    private TypeSeance typeSeance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Matiere.Semestre semestre;

    @ManyToOne
    @JoinColumn(name = "creneau_id", nullable = false)
    private Creneau creneau;

    @ManyToOne
    @JoinColumn(name = "enseignant_id")
    private Enseignant enseignant;

    private String salle; // e.g., "Amphi A", "Salle 201"

    public enum JourSemaine {
        LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI
    }

    public enum TypeSeance {
        COURS, TD
    }
}
