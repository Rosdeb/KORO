package com.koro.app.concept.controller;

import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.concept.entity.Category;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.translation.repository.TranslationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ConceptController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private TranslationRepository translationRepository;

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

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody Category request) {
        return categoryRepository.findById(id)
                .map(category -> {
                    if (request.getName() != null) category.setName(request.getName());
                    if (request.getDescription() != null) category.setDescription(request.getDescription());
                    return ResponseEntity.ok(categoryRepository.save(category));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCategory(@PathVariable String id) {
        return categoryRepository.findById(id)
                .map(category -> {
                    categoryRepository.delete(category);
                    return ResponseEntity.ok(new MessageResponse("Category deleted successfully!"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // --- Concept APIs ---
    @GetMapping("/concepts")
    public ResponseEntity<?> getAllConcepts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "name"));

        Page<Concept> conceptPage;
        if (categoryId != null && !categoryId.isBlank()) {
            conceptPage = conceptRepository.findByCategoryId(categoryId, pageable);
        } else {
            conceptPage = conceptRepository.findAll(pageable);
        }

        List<Map<String, Object>> content = new ArrayList<>();
        for (Concept concept : conceptPage.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", concept.getId());
            item.put("name", concept.getName());
            item.put("description", concept.getDescription());
            item.put("referenceImage", concept.getReferenceImage());
            item.put("categoryId", concept.getCategory() != null ? concept.getCategory().getId() : null);
            item.put("categoryName", concept.getCategory() != null ? concept.getCategory().getName() : null);
            item.put("createdAt", concept.getCreatedAt());
            item.put("updatedAt", concept.getUpdatedAt());
            content.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", conceptPage.getNumber());
        response.put("size", conceptPage.getSize());
        response.put("totalElements", conceptPage.getTotalElements());
        response.put("totalPages", conceptPage.getTotalPages());
        response.put("hasNext", conceptPage.hasNext());
        response.put("hasPrevious", conceptPage.hasPrevious());
        return ResponseEntity.ok(response);
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

    @PutMapping("/admin/concepts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> updateConcept(@PathVariable String id, @RequestBody Concept request) {
        return conceptRepository.findById(id)
                .map(concept -> {
                    if (request.getName() != null) concept.setName(request.getName());
                    if (request.getDescription() != null) concept.setDescription(request.getDescription());
                    if (request.getReferenceImage() != null) concept.setReferenceImage(request.getReferenceImage());
                    if (request.getCategory() != null && request.getCategory().getId() != null) {
                        Category category = categoryRepository.findById(request.getCategory().getId())
                                .orElseThrow(() -> new RuntimeException("Category not found"));
                        concept.setCategory(category);
                    }
                    return ResponseEntity.ok(conceptRepository.save(concept));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/concepts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteConcept(@PathVariable String id) {
        return conceptRepository.findById(id)
                .<ResponseEntity<?>>map(concept -> {
                    if (!translationRepository.findByConceptId(id).isEmpty()) {
                        return ResponseEntity.badRequest().body(new MessageResponse(
                                "Error: Concept has existing translations and cannot be deleted"));
                    }
                    conceptRepository.delete(concept);
                    return ResponseEntity.ok(new MessageResponse("Concept deleted successfully!"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
