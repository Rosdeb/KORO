package com.koro.app.collection.dto;

import com.koro.app.collection.entity.CollectionItem;
import com.koro.app.translation.entity.Translation;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionItemResponse {
    private String id;
    private String conceptId;
    private String conceptName;
    private String categoryName;
    private String languageId;
    private String languageName;
    private String translationText;
    private String pronunciation;
    private String notes;
    private String chapter;
    private Integer displayOrder;
    private LocalDateTime createdAt;

    public static CollectionItemResponse build(CollectionItem item, Translation translation) {
        return CollectionItemResponse.builder()
                .id(item.getId())
                .conceptId(item.getConcept().getId())
                .conceptName(item.getConcept().getName())
                .categoryName(item.getConcept().getCategory() != null ? item.getConcept().getCategory().getName() : null)
                .languageId(item.getLanguage().getId())
                .languageName(item.getLanguage().getName())
                .translationText(translation != null ? translation.getText() : null)
                .pronunciation(translation != null ? translation.getPronunciation() : null)
                .notes(item.getNotes())
                .chapter(item.getChapter())
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
