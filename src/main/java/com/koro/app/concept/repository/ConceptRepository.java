package com.koro.app.concept.repository;

import com.koro.app.concept.entity.Concept;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptRepository extends MongoRepository<Concept, String> {
    Optional<Concept> findByNameIgnoreCase(String name);
    List<Concept> findByCategoryId(String categoryId);
    Page<Concept> findByCategoryId(String categoryId, Pageable pageable);
    Page<Concept> findAll(Pageable pageable);
    List<Concept> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);
}
