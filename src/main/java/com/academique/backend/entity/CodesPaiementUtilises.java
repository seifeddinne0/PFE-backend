package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "codes_paiement_utilises")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodesPaiementUtilises {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codePaiement;

    @Column(nullable = false)
    private Long paiementId;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
