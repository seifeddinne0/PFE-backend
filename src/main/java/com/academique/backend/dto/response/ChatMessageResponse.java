package com.academique.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long senderId;
    private String senderNom;
    private String senderPrenom;
    private String senderPhoto;
    private String senderRole;
    private Long receiverId;
    private String receiverNom;
    private String receiverPrenom;
    private Long classeId;
    private String classeNom;
    private String content;
    private LocalDateTime sentAt;
    private boolean isRead;
    private boolean isDeleted;
    private boolean isEdited;
}
