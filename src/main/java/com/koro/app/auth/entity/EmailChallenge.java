package com.koro.app.auth.entity;

import com.koro.app.user.entity.User;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("email_challenges")
public class EmailChallenge {
    @Id private String id;
    private String email;
    private String purpose;
    private String codeHash;
    private String generation;
    private Instant expiresAt;
    private Instant nextSendAt;
    private int attempts;
    private int sends;
    private User pendingUser;
}
