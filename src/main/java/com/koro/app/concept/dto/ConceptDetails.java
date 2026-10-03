package com.koro.app.concept.dto;

import org.springframework.data.annotation.Id;

/** Read model without document references, so mapping cannot trigger extra queries. */
public record ConceptDetails(
        @Id String id,
        String name,
        String description,
        CategoryDetails category,
        String referenceImage) {

    public record CategoryDetails(@Id String id, String name, String description) {}
}
