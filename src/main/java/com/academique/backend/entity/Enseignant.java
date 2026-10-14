package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enseignants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enseignant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String matricule;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(unique = true, nullable = false)
    private String email;

    private String telephone;
    private LocalDate dateNaissance;
    private String adresse;
    private String specialite;
    private String grade;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean canManageNotes = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIF;

    public enum Statut {
        ACTIF, INACTIF, SUSPENDU
    }

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filiere_id")
    private Filiere filiere;
}