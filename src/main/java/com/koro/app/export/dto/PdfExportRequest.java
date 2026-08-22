package com.koro.app.export.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PdfExportRequest {
    @NotBlank
    private String collectionId;

    @NotBlank
    private String languageId;
}
