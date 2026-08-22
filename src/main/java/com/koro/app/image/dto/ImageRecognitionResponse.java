package com.koro.app.image.dto;

import com.koro.app.image.entity.ImageRecognitionResult;
import com.koro.app.translation.dto.TranslationResponse;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageRecognitionResponse {
    private String id;
    private String imageUrl;
    private String detectedLabel;
    private Double confidence;
    private String conceptId;
    private String conceptName;
    private String categoryName;
    private List<TranslationResponse> translations;
    private LocalDateTime createdAt;

    public static ImageRecognitionResponse build(ImageRecognitionResult res, List<TranslationResponse> translations) {
        return ImageRecognitionResponse.builder()
                .id(res.getId())
                .imageUrl(res.getImageUrl())
                .detectedLabel(res.getDetectedLabel())
                .confidence(res.getConfidence())
                .conceptId(res.getConcept() != null ? res.getConcept().getId() : null)
                .conceptName(res.getConcept() != null ? res.getConcept().getName() : null)
                .categoryName(res.getConcept() != null && res.getConcept().getCategory() != null ? res.getConcept().getCategory().getName() : null)
                .translations(translations)
                .createdAt(res.getCreatedAt())
                .build();
    }
}
