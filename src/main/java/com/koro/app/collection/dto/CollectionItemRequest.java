package com.koro.app.collection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CollectionItemRequest {
    @NotBlank
    private String conceptId;

    @NotBlank
    private String languageId;

    private String notes;
    private String chapter;
    private Integer displayOrder;
}
