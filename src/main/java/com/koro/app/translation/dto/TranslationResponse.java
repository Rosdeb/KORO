package com.koro.app.translation.dto;

import com.koro.app.translation.entity.Translation;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationResponse {
    private String id;
    private String conceptId;
    private String conceptName;
    private String categoryName;
    private String languageId;
    private String languageName;
    private String text;
    private String pronunciation;
    private boolean verified;
    private String notes;
    private String exampleSentence;

    public static TranslationResponse fromTranslation(Translation t) {
        return TranslationResponse.builder()
                .id(t.getId())
                .conceptId(t.getConcept().getId())
                .conceptName(t.getConcept().getName())
                .categoryName(t.getConcept().getCategory() != null ? t.getConcept().getCategory().getName() : null)
                .languageId(t.getLanguage().getId())
                .languageName(t.getLanguage().getName())
                .text(t.getText())
                .pronunciation(t.getPronunciation())
                .verified(t.isVerified())
                .notes(t.getNotes())
                .exampleSentence(t.getExampleSentence())
                .build();
    }
}
