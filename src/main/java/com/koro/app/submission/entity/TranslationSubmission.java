package com.koro.app.submission.entity;

import com.koro.app.concept.entity.Concept;
import com.koro.app.language.entity.Language;
import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "translation_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationSubmission {

    @Id
    private String id;

    @DocumentReference
    private Concept concept;

    @DocumentReference
    private Language language;

    private String suggestedTranslation;

    private String pronunciation;

    private String notes;

    @DocumentReference
    private User submittedBy;

    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    private String reviewerNote;

    @DocumentReference
    private User reviewedBy;

    @CreatedDate
    private LocalDateTime createdAt;
    
    private LocalDateTime reviewedAt;
}
