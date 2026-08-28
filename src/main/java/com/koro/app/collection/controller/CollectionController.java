package com.koro.app.collection.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.collection.CollectionOrdering;
import com.koro.app.collection.dto.BulkCollectionItemRequest;
import com.koro.app.collection.dto.ChapterOrderRequest;
import com.koro.app.collection.dto.CollectionItemRequest;
import com.koro.app.collection.dto.CollectionItemResponse;
import com.koro.app.collection.dto.CollectionItemUpdateRequest;
import com.koro.app.collection.dto.CollectionRequest;
import com.koro.app.collection.dto.CollectionResponse;
import com.koro.app.collection.dto.CollectionSummaryResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public ResponseEntity<List<CollectionSummaryResponse>> getMyCollections() {
        User user = getCurrentUser();
        List<CollectionSummaryResponse> summaries = collectionRepository.findByUserId(user.getId()).stream()
                .map(col -> {
                    List<CollectionItem> items = itemRepository.findByCollectionId(col.getId());
                    long chapterCount = items.stream()
                            .map(it -> it.getChapter() == null ? "General" : it.getChapter())
                            .distinct()
                            .count();
                    return CollectionSummaryResponse.build(col, items.size(), (int) chapterCount);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
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
        return ResponseEntity.ok(CollectionResponse.build(saved, List.of()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCollectionDetails(@PathVariable String id) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        List<CollectionItem> items = itemRepository.findByCollectionId(id);
        List<String> chapterOrder = CollectionOrdering.resolveChapterOrder(
                col.getChapterOrder(),
                items.stream().map(it -> it.getChapter() == null ? "General" : it.getChapter()).collect(Collectors.toSet()));
        items.sort(CollectionOrdering.itemComparator(chapterOrder));

        List<CollectionItemResponse> itemResponses = items.stream()
                .map(item -> CollectionItemResponse.build(item, translationFor(item)))
                .collect(Collectors.toList());

        CollectionResponse body = CollectionResponse.build(col, itemResponses);
        body.setChapterOrder(chapterOrder);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCollection(@PathVariable String id, @Valid @RequestBody CollectionRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        col.setName(request.getName());
        col.setDescription(request.getDescription());
        Collection saved = collectionRepository.save(col);

        return ResponseEntity.ok(CollectionResponse.build(saved, List.of()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCollection(@PathVariable String id) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        itemRepository.deleteByCollectionId(id);
        collectionRepository.delete(col);

        activityLogService.log(ActivityType.REMOVE_SAVED_WORD, "Deleted collection book: " + col.getName(), id, null);
        return ResponseEntity.ok(new MessageResponse("Collection deleted successfully"));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItemToCollection(@PathVariable String id, @Valid @RequestBody CollectionItemRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        Concept concept = conceptRepository.findById(request.getConceptId()).orElse(null);
        Language language = languageRepository.findById(request.getLanguageId()).orElse(null);
        if (concept == null || language == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Concept or language not found"));
        }

        if (itemRepository.findByCollectionIdAndConceptIdAndLanguageId(id, concept.getId(), language.getId()).isPresent()) {
            return ResponseEntity.status(409).body(new MessageResponse("Error: Vocabulary already saved in this collection."));
        }

        String chapter = request.getChapter() != null ? request.getChapter() : "General";
        int displayOrder = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : nextDisplayOrder(id, chapter);

        CollectionItem item = CollectionItem.builder()
                .collection(col)
                .concept(concept)
                .language(language)
                .notes(request.getNotes())
                .chapter(chapter)
                .displayOrder(displayOrder)
                .build();

        CollectionItem saved = itemRepository.save(item);
        activityLogService.log(ActivityType.SAVE_WORD,
                "Saved word '" + concept.getName() + "' to collection '" + col.getName() + "'", saved.getId(), null);

        return ResponseEntity.ok(CollectionItemResponse.build(saved, translationFor(saved)));
    }

    @PostMapping("/{id}/items/bulk")
    public ResponseEntity<?> addItemsBulk(@PathVariable String id, @Valid @RequestBody BulkCollectionItemRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        Language language = languageRepository.findById(request.getLanguageId()).orElse(null);
        if (language == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Language not found"));
        }

        String chapter = request.getChapter() != null ? request.getChapter() : "General";
        int nextOrder = nextDisplayOrder(id, chapter);

        List<CollectionItemResponse> added = new ArrayList<>();
        List<Map<String, String>> skipped = new ArrayList<>();

        for (String conceptId : new LinkedHashSet<>(request.getConceptIds())) {
            Concept concept = conceptRepository.findById(conceptId).orElse(null);
            if (concept == null) {
                skipped.add(Map.of("conceptId", conceptId, "reason", "concept not found"));
                continue;
            }
            if (itemRepository.findByCollectionIdAndConceptIdAndLanguageId(id, conceptId, language.getId()).isPresent()) {
                skipped.add(Map.of("conceptId", conceptId, "reason", "already in collection"));
                continue;
            }
            CollectionItem saved = itemRepository.save(CollectionItem.builder()
                    .collection(col)
                    .concept(concept)
                    .language(language)
                    .notes(request.getNotes())
                    .chapter(chapter)
                    .displayOrder(nextOrder++)
                    .build());
            added.add(CollectionItemResponse.build(saved, translationFor(saved)));
        }

        if (!added.isEmpty()) {
            activityLogService.log(ActivityType.SAVE_WORD,
                    "Saved " + added.size() + " word(s) to collection '" + col.getName() + "'", id, null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("added", added);
        body.put("skipped", skipped);
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> updateItem(@PathVariable String id, @PathVariable String itemId,
                                        @RequestBody CollectionItemUpdateRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        CollectionItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || !item.getCollection().getId().equals(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Item does not belong to this collection."));
        }

        if (request.getLanguageId() != null && !request.getLanguageId().equals(item.getLanguage().getId())) {
            Language language = languageRepository.findById(request.getLanguageId()).orElse(null);
            if (language == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Language not found"));
            }
            boolean clash = itemRepository
                    .findByCollectionIdAndConceptIdAndLanguageId(id, item.getConcept().getId(), language.getId())
                    .filter(existing -> !existing.getId().equals(itemId))
                    .isPresent();
            if (clash) {
                return ResponseEntity.status(409).body(new MessageResponse(
                        "Error: This word already exists in the collection in that language."));
            }
            item.setLanguage(language);
        }
        if (request.getChapter() != null) {
            item.setChapter(request.getChapter().isBlank() ? "General" : request.getChapter());
        }
        if (request.getNotes() != null) {
            item.setNotes(request.getNotes());
        }
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }

        CollectionItem saved = itemRepository.save(item);
        return ResponseEntity.ok(CollectionItemResponse.build(saved, translationFor(saved)));
    }

    @PutMapping("/{id}/chapters")
    public ResponseEntity<?> reorderChapters(@PathVariable String id, @Valid @RequestBody ChapterOrderRequest request) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        List<CollectionItem> items = itemRepository.findByCollectionId(id);
        List<String> order = new ArrayList<>();

        for (ChapterOrderRequest.ChapterOp op : request.getChapters()) {
            if (op.getFrom() == null || op.getFrom().isBlank()) {
                continue;
            }
            String target = (op.getTo() != null && !op.getTo().isBlank()) ? op.getTo() : op.getFrom();
            if (!target.equals(op.getFrom())) {
                for (CollectionItem item : items) {
                    if (op.getFrom().equals(item.getChapter())) {
                        item.setChapter(target);
                    }
                }
            }
            if (!order.contains(target)) {
                order.add(target);
            }
        }

        itemRepository.saveAll(items);

        // Append any chapters the request did not mention so the stored order stays complete.
        Set<String> present = items.stream()
                .map(it -> it.getChapter() == null ? "General" : it.getChapter())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        col.setChapterOrder(CollectionOrdering.resolveChapterOrder(order, present));
        collectionRepository.save(col);

        items.sort(CollectionOrdering.itemComparator(col.getChapterOrder()));
        List<CollectionItemResponse> itemResponses = items.stream()
                .map(item -> CollectionItemResponse.build(item, translationFor(item)))
                .collect(Collectors.toList());
        CollectionResponse body = CollectionResponse.build(col, itemResponses);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> removeItemFromCollection(@PathVariable String id, @PathVariable String itemId) {
        User user = getCurrentUser();
        Collection col = collectionRepository.findById(id).orElse(null);
        if (col == null) {
            return ResponseEntity.notFound().build();
        }
        if (!col.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Forbidden access"));
        }

        CollectionItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || !item.getCollection().getId().equals(id)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Item does not belong to this collection."));
        }

        itemRepository.delete(item);
        activityLogService.log(ActivityType.REMOVE_SAVED_WORD,
                "Removed word '" + item.getConcept().getName() + "' from collection '" + col.getName() + "'", itemId, null);
        return ResponseEntity.ok(new MessageResponse("Vocabulary removed from collection"));
    }

    private Translation translationFor(CollectionItem item) {
        return translationRepository
                .findByConceptIdAndLanguageId(item.getConcept().getId(), item.getLanguage().getId())
                .orElse(null);
    }

    private int nextDisplayOrder(String collectionId, String chapter) {
        return itemRepository.findByCollectionId(collectionId).stream()
                .filter(it -> chapter.equals(it.getChapter()))
                .map(it -> it.getDisplayOrder() == null ? 0 : it.getDisplayOrder())
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(0);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
