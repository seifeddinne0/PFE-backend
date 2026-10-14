package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "validations_enseignants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationEnseignant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.EN_ATTENTE;

    public enum Statut {
        EN_ATTENTE, VALIDE, REJETE
    }

    private String commentaire;
    private LocalDateTime dateValidation;

    @ManyToOne
    @JoinColumn(name = "demande_id", nullable = false)
    private DemandeDocument demande;

    @ManyToOne
    @JoinColumn(name = "enseignant_id", nullable = false)
    private Enseignant enseignant;
}