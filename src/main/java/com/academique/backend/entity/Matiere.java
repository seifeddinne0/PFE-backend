package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "matieres")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Matiere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    private String code;
    private String description;

    @Column(nullable = false)
    private Double coefficient = 1.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Semestre semestre = Semestre.S1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enseignant_id")
    private Enseignant enseignant;

    public enum Semestre {
        S1, S2, S3, S4, S5
    }
}