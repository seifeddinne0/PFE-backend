package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContactResponse {
    private Long userId;
    private Long entityId;
    private String nom;
    private String prenom;
    private String email;
    private String photo;
    private String role;
    private String lastMessage;
    private String lastMessageTime;
    private long unreadCount;
    // For groups
    private Long classeId;
    private String classeCode;
    private String classeNom;
    private int memberCount;
    private java.time.LocalDateTime sentAtRaw;
}
