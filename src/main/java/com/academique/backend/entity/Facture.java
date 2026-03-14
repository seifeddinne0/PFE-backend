package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "factures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(nullable = false)
    private Double montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.NON_PAYEE;

    public enum Statut {
        PAYEE, NON_PAYEE, EN_ATTENTE, ANNULEE
    }

    @Enumerated(EnumType.STRING)
    private TypeFacture typeFacture;

    public enum TypeFacture {
        SCOLARITE, INSCRIPTION, BIBLIOTHEQUE, AUTRE
    }

    private String description;
    private LocalDate dateEcheance;
    private LocalDate datePaiement;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;
}