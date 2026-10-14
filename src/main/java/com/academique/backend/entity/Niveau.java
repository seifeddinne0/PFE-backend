package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "niveaux")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Niveau {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // LCS1, LCS2, LCS3, LBC1, etc.

    @Column(nullable = false)
    private String nom;

    @ManyToOne
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @OneToMany(mappedBy = "niveau", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Classe> classes;
}
