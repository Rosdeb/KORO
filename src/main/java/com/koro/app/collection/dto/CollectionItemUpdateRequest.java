package com.koro.app.collection.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Partial update for a single {@link com.koro.app.collection.entity.CollectionItem}.
 * Every field is optional — only non-null fields are applied. Sending {@code notes}
 * as an empty string clears the note.
 */
@Getter
@Setter
public class CollectionItemUpdateRequest {
    private String chapter;
    private String notes;
    private Integer displayOrder;
    private String languageId;
}
