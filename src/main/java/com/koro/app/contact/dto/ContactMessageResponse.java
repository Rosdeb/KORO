package com.koro.app.contact.dto;

import com.koro.app.contact.entity.ContactMessage;
import com.koro.app.contact.entity.ContactStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessageResponse {

    private String id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private ContactStatus status;
    private String userId;
    private String replySubject;
    private String replyMessage;
    private String repliedBy;
    private String repliedByUserId;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactMessageResponse fromEntity(ContactMessage msg) {
        if (msg == null) return null;
        return ContactMessageResponse.builder()
                .id(msg.getId())
                .name(msg.getName())
                .email(msg.getEmail())
                .subject(msg.getSubject())
                .message(msg.getMessage())
                .status(msg.getStatus())
                .userId(msg.getUserId())
                .replySubject(msg.getReplySubject())
                .replyMessage(msg.getReplyMessage())
                .repliedBy(msg.getRepliedBy())
                .repliedByUserId(msg.getRepliedByUserId())
                .repliedAt(msg.getRepliedAt())
                .createdAt(msg.getCreatedAt())
                .updatedAt(msg.getUpdatedAt())
                .build();
    }
}
