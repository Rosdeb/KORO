package com.koro.app.translation.entity;

import com.koro.app.concept.entity.Concept;
import com.koro.app.language.entity.Language;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "translations")
@CompoundIndex(name = "concept_lang_idx", def = "{'concept': 1, 'language': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Translation {

    @Id
    private String id;

    @DocumentReference
    @org.springframework.data.mongodb.core.index.Indexed
    private Concept concept;

    @DocumentReference
    @org.springframework.data.mongodb.core.index.Indexed
    private Language language;

    @org.springframework.data.mongodb.core.index.Indexed
    private String text;

    @org.springframework.data.mongodb.core.index.Indexed
    private String pronunciation;

    @Builder.Default
    private boolean verified = true;

    private String notes;

    private String exampleSentence;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
