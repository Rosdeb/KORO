package com.koro.app.concept.controller;

import com.koro.app.concept.entity.Category;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.concept.repository.ConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ConceptController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    // --- Category APIs ---
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createCategory(@RequestBody Category category) {
        if (categoryRepository.findByNameIgnoreCase(category.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("Category already exists");
        }
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    // --- Concept APIs ---
    @GetMapping("/concepts")
    public ResponseEntity<List<Concept>> getAllConcepts(@RequestParam(required = false) String categoryId) {
        if (categoryId != null) {
            return ResponseEntity.ok(conceptRepository.findByCategoryId(categoryId));
        }
        return ResponseEntity.ok(conceptRepository.findAll());
    }

    @GetMapping("/concepts/{id}")
    public ResponseEntity<?> getConceptById(@PathVariable String id) {
        return conceptRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/concepts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createConcept(@RequestBody Concept request) {
        if (conceptRepository.findByNameIgnoreCase(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("Concept already exists");
        }
        
        Category category = null;
        if (request.getCategory() != null && request.getCategory().getId() != null) {
            category = categoryRepository.findById(request.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
        }

        Concept concept = Concept.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .referenceImage(request.getReferenceImage())
                .build();

        return ResponseEntity.ok(conceptRepository.save(concept));
    }
}
