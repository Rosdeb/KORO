package com.koro.app.export.dto;

import com.koro.app.export.entity.PdfExport;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Safe view of a {@link PdfExport} record. The entity holds {@code @DocumentReference}
 * links to the full {@code User} and {@code Collection}; serialising it directly leaked
 * the user's password hash, so responses use this instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfExportResponse {
    private String id;
    private String collectionId;
    private String collectionName;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private LocalDateTime createdAt;

    public static PdfExportResponse from(PdfExport export) {
        String collectionId = null;
        String collectionName = null;
        try {
            if (export.getCollection() != null) {
                collectionId = export.getCollection().getId();
                collectionName = export.getCollection().getName();
            }
        } catch (RuntimeException ignored) {
            // dangling reference — leave collection fields null
        }
        return PdfExportResponse.builder()
                .id(export.getId())
                .collectionId(collectionId)
                .collectionName(collectionName)
                .fileName(export.getFileName())
                .fileUrl(export.getFileUrl())
                .fileSize(export.getFileSize())
                .createdAt(export.getCreatedAt())
                .build();
    }
}
