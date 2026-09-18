package com.koro.app.concept.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Document(collection = "concepts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concept {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;

    @DocumentReference
    @Indexed
    private Category category;

    private String referenceImage;
}
