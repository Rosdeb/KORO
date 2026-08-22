package com.koro.app.activity.entity;

import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    private String id;

    @DocumentReference(lazy = true)
    private User user;

    private ActivityType activityType;

    private String description;

    private String referenceId; // Changed to String to support MongoDB string IDs!

    private String metadata;

    private String ipAddress;

    private String device;

    @CreatedDate
    private LocalDateTime createdAt;
}
