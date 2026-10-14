package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double valeur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNote typeNote = TypeNote.EXAMEN;

    public enum TypeNote {
        EXAMEN, CONTROLE, TP, PROJET
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Semestre semestre = Semestre.S1;

    public enum Semestre {
        S1, S2, S3, S4, S5
    }

    private String commentaire;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    @ManyToOne
    @JoinColumn(name = "enseignant_id")
    private Enseignant enseignant;
}