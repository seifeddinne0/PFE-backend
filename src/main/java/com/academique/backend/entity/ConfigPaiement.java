package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "config_paiement")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String rib;

    @Column(nullable = false)
    private boolean actif = false;
}
