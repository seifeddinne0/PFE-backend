package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String typeDocument;

    @Column(nullable = false)
    private boolean enabled = true;
    
    private Integer levelRequired; // null if no restriction
}
