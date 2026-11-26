package com.koro.app.translation.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "ai_db_search")
@CompoundIndex(name = "query_target_lang_idx", def = "{'normalizedQuery': 1, 'targetLanguageId': 1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSearchCache {

    @Id
    private String id;

    @Indexed
    private String normalizedQuery;

    private String rawQuery;

    @Indexed
    private String sourceLanguageId;

    @Indexed
    private String targetLanguageId;

    private String targetLanguageName;

    private String targetText;

    private String pronunciation;

    private String notes;

    private String exampleSentence;

    @Builder.Default
    private List<String> groundingVocab = new ArrayList<>();

    private String modelUsed;

    @Builder.Default
    private int hitCount = 1;

    private LocalDateTime lastHitAt;

    @Builder.Default
    private boolean verified = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
