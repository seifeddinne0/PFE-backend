package com.academique.backend.dto.request;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private Long receiverId;   // for direct messages
    private Long classeId;     // for group messages
    private String content;
}
