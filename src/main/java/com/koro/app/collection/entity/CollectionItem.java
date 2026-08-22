package com.koro.app.collection.entity;

import com.koro.app.concept.entity.Concept;
import com.koro.app.language.entity.Language;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "collection_items")
@CompoundIndex(name = "col_concept_lang_idx", def = "{'collection': 1, 'concept': 1, 'language': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionItem {

    @Id
    private String id;

    @DocumentReference(lazy = true)
    private Collection collection;

    @DocumentReference
    private Concept concept;

    @DocumentReference
    private Language language;

    private String notes;

    @Builder.Default
    private String chapter = "General";

    @Builder.Default
    private Integer displayOrder = 0;

    @CreatedDate
    private LocalDateTime createdAt;
}
