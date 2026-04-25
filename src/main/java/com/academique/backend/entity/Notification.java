package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String type; // e.g., "NOTE", "ABSENCE", "FACTURE", "DOCUMENT", "CHAT"

    private boolean isRead;

    private LocalDateTime createdAt;

    private Long userId; // The ID of the recipient user (Student, Teacher, or Admin)

    private String userRole; // "ETUDIANT", "ENSEIGNANT", "ADMIN"

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isRead = false;
    }
}
