package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "etudiants")
public class Etudiant {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIF;

    @Column
    private Boolean notesAccessEnabled = false;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "classe_id")
    private Classe classe;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    public enum Statut {
        ACTIF, INACTIF, SUSPENDU
    }
}