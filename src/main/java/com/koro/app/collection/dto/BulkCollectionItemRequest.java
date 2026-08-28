package com.koro.app.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Adds many concepts to a book in one request (the "Add words" dialog otherwise
 * fires one call per word). All concepts are added in the same {@code languageId}
 * and {@code chapter}; already-present concepts are reported in {@code skipped}
 * rather than failing the whole call.
 */
@Getter
@Setter
public class BulkCollectionItemRequest {

    @NotBlank
    private String languageId;

    @NotEmpty
    private List<String> conceptIds;

    private String chapter;
    private String notes;
}
