package com.koro.app.translation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TranslationSearchRequest {
    private String sourceLanguageId;
    private String targetLanguageId;
    private String query;
}
