package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "filieres")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filiere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // LCS, LBC, LCE

    @Column(nullable = false)
    private String nom; // Licence Computer Science, etc.

    @OneToMany(mappedBy = "filiere", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Niveau> niveaux;
}
