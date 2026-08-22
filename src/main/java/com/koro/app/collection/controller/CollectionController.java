package com.koro.app.collection.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.collection.dto.CollectionItemRequest;
import com.koro.app.collection.dto.CollectionItemResponse;
import com.koro.app.collection.dto.CollectionRequest;
import com.koro.app.collection.dto.CollectionResponse;
import com.koro.app.collection.entity.Collection;
import com.koro.app.collection.entity.CollectionItem;
import com.koro.app.collection.repository.CollectionItemRepository;
import com.koro.app.collection.repository.CollectionRepository;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private CollectionItemRepository itemRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    public ResponseEntity<List<Collection>> getMyCollections() {
        User user = getCurrentUser();
        return ResponseEntity.ok(collectionRepository.findByUserId(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> createCollection(@Valid @RequestBody CollectionRequest request) {
        User user = getCurrentUser();
        Collection col = Collection.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(user)
                .build();
        
        Collection saved = collectionRepository.save(col);
        activityLogService.log(ActivityType.ADD_VOCABULARY, "Created collection: " + saved.getName(), saved.getId(), null);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCollectionDetails(@PathVariable String id) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        List<CollectionItem> items = itemRepository.findByCollectionId(id);
        List<CollectionItemResponse> itemResponses = items.stream().map(item -> {
            Translation t = translationRepository.findByConceptIdAndLanguageId(
                    item.getConcept().getId(), 
                    item.getLanguage().getId()
            ).orElse(null);
            return CollectionItemResponse.build(item, t);
        }).collect(Collectors.toList());

        return ResponseEntity.ok(CollectionResponse.build(col, itemResponses));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCollection(@PathVariable String id, @Valid @RequestBody CollectionRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        col.setName(request.getName());
        col.setDescription(request.getDescription());
        Collection saved = collectionRepository.save(col);

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCollection(@PathVariable String id) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        // Delete all items first (cascade or manual)
        List<CollectionItem> items = itemRepository.findByCollectionId(id);
        itemRepository.deleteAll(items);
        collectionRepository.delete(col);

        activityLogService.log(ActivityType.REMOVE_SAVED_WORD, "Deleted collection book: " + col.getName(), id, null);

        return ResponseEntity.ok(new MessageResponse("Collection deleted successfully"));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItemToCollection(@PathVariable String id, @Valid @RequestBody CollectionItemRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        Concept concept = conceptRepository.findById(request.getConceptId())
                .orElseThrow(() -> new RuntimeException("Concept not found"));
        Language language = languageRepository.findById(request.getLanguageId())
                .orElseThrow(() -> new RuntimeException("Language not found"));

        // Check if item already exists
        if (itemRepository.findByCollectionIdAndConceptIdAndLanguageId(id, concept.getId(), language.getId()).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Vocabulary already saved in this collection."));
        }

        CollectionItem item = CollectionItem.builder()
                .collection(col)
                .concept(concept)
                .language(language)
                .notes(request.getNotes())
                .chapter(request.getChapter() != null ? request.getChapter() : "General")
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        CollectionItem saved = itemRepository.save(item);

        activityLogService.log(
                ActivityType.SAVE_WORD, 
                "Saved word '" + concept.getName() + "' to collection '" + col.getName() + "'", 
                saved.getId(), 
                null
        );

        Translation t = translationRepository.findByConceptIdAndLanguageId(concept.getId(), language.getId()).orElse(null);
        return ResponseEntity.ok(CollectionItemResponse.build(saved, t));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> removeItemFromCollection(@PathVariable String id, @PathVariable String itemId) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        CollectionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("CollectionItem not found"));
        
        if (!item.getCollection().getId().equals(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Item does not belong to this collection."));
        }

        itemRepository.delete(item);

        activityLogService.log(
                ActivityType.REMOVE_SAVED_WORD, 
                "Removed word '" + item.getConcept().getName() + "' from collection '" + col.getName() + "'", 
                itemId, 
                null
        );

        return ResponseEntity.ok(new MessageResponse("Vocabulary removed from collection"));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
