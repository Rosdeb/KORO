package com.koro.app.submission.entity;

import com.koro.app.concept.entity.Category;
import com.koro.app.language.entity.Language;
import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.index.Indexed;
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
    @Indexed
    private Category category;

    @DocumentReference
    @Indexed
    private Language sourceLanguage;

    private String sourceWord;

    private String banglaTranslation;

    private String englishTranslation;

    private String pronunciation;

    private String exampleSentence;

    private String notes;

    @DocumentReference
    @Indexed
    private User submittedBy;

    @Builder.Default
    @Indexed
    private SubmissionStatus status = SubmissionStatus.PENDING;

    private String reviewerNote;

    private String rejectionReason;

    @DocumentReference
    @Indexed
    private User reviewedBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime reviewedAt;
}
