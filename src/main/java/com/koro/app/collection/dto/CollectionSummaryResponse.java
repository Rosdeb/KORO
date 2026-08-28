package com.koro.app.collection.dto;

import com.koro.app.collection.entity.Collection;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Lightweight shape for the "My Books" list: everything a book card needs
 * (including the word / chapter counts) without the full {@code items} array.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionSummaryResponse {
    private String id;
    private String name;
    private String description;
    private long itemCount;
    private int chapterCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CollectionSummaryResponse build(Collection col, long itemCount, int chapterCount) {
        return CollectionSummaryResponse.builder()
                .id(col.getId())
                .name(col.getName())
                .description(col.getDescription())
                .itemCount(itemCount)
                .chapterCount(chapterCount)
                .createdAt(col.getCreatedAt())
                .updatedAt(col.getUpdatedAt())
                .build();
    }
}
