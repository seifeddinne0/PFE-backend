package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demandes_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDocument typeDocument;

    public enum TypeDocument {
        ATTESTATION_PRESENCE,
        RELEVE_NOTES,
        FACTURE_PAIEMENT,
        DEMANDE_STAGE,
        VALIDATION_STAGE,
        ATTESTATION_REUSSITE,
        ATTESTATION_AFFECTATION
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EN_ATTENTE;

    public enum Statut {
        EN_ATTENTE,
        EN_COURS_VALIDATION,
        VALIDEE,
        REJETEE,
        ENVOYEE
    }

    private String motif;
    private String commentaireAdmin;
    private String nomEntrepriseStage; // pour DEMANDE_STAGE / VALIDATION_STAGE
    private String adresseEntreprise;
    private String nomEncadrant;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    // Pour ATTESTATION_PRESENCE : 2 enseignants validateurs
    @ManyToMany
    @JoinTable(
        name = "demande_validateurs",
        joinColumns = @JoinColumn(name = "demande_id"),
        inverseJoinColumns = @JoinColumn(name = "enseignant_id")
    )
    private List<Enseignant> validateurs = new ArrayList<>();

    // Validations des enseignants
    @OneToMany(mappedBy = "demande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValidationEnseignant> validations = new ArrayList<>();
}