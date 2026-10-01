package com.koro.app.contact.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessage {

    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed
    private String email;

    private String subject;

    private String message;

    @Builder.Default
    @Indexed
    private ContactStatus status = ContactStatus.PENDING;

    @Indexed
    private String userId;

    private String replySubject;

    private String replyMessage;

    private String repliedBy;

    private String repliedByUserId;

    private LocalDateTime repliedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
