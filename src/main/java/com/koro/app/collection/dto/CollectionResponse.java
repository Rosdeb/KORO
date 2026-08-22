package com.koro.app.collection.dto;

import com.koro.app.collection.entity.Collection;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionResponse {
    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CollectionItemResponse> items;

    public static CollectionResponse build(Collection col, List<CollectionItemResponse> items) {
        return CollectionResponse.builder()
                .id(col.getId())
                .name(col.getName())
                .description(col.getDescription())
                .createdAt(col.getCreatedAt())
                .updatedAt(col.getUpdatedAt())
                .items(items)
                .build();
    }
}
