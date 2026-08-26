package com.koro.app.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionRequest {
    @NotBlank
    private String categoryId;

    @NotBlank
    private String sourceLanguageId;

    @NotBlank
    private String sourceWord;

    @NotBlank
    private String banglaTranslation;

    @NotBlank
    private String englishTranslation;

    private String pronunciation;

    private String exampleSentence;

    private String note;
}
