package com.koro.app.image.entity;

import com.koro.app.concept.entity.Concept;
import com.koro.app.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import java.time.LocalDateTime;

@Document(collection = "image_recognition_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageRecognitionResult {

    @Id
    private String id;

    @DocumentReference(lazy = true)
    private User user;

    private String imageUrl;

    private String detectedLabel;

    private Double confidence;

    @DocumentReference
    private Concept concept;

    @CreatedDate
    private LocalDateTime createdAt;
}
