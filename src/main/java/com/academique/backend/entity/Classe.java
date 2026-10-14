package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // LCS1A, LCS1B, LCS1C, LCS1D, etc.

    @Column(nullable = false)
    private String nom;

    @ManyToOne
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    @OneToMany(mappedBy = "classe")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Etudiant> etudiants;
}
