package com.koro.app.concept.repository;

import com.koro.app.concept.entity.Concept;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptRepository extends MongoRepository<Concept, String> {
    Optional<Concept> findByNameIgnoreCase(String name);
    List<Concept> findByCategoryId(String categoryId);
    List<Concept> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
}
