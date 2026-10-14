package com.academique.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User receiver; // null for group messages

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id")
    private Classe classe; // null for direct messages

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isEdited = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deletedForSender = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deletedForReceiver = false;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
