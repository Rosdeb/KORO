package com.koro.app.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionRequest {
    @NotBlank
    private String conceptId;

    @NotBlank
    private String languageId;

    @NotBlank
    private String suggestedTranslation;

    private String pronunciation;

    private String notes;
}
