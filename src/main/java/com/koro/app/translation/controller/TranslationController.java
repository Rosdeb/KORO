//package com.koro.app.translation.controller;
//
//import com.koro.app.activity.entity.ActivityType;
//import com.koro.app.activity.service.ActivityLogService;
//import com.koro.app.auth.dto.MessageResponse;
//import com.koro.app.common.TextNormalizer;
//import com.koro.app.concept.entity.Concept;
//import com.koro.app.concept.repository.ConceptRepository;
//import com.koro.app.language.entity.Language;
//import com.koro.app.language.repository.LanguageRepository;
//import com.koro.app.translation.dto.TranslationSearchRequest;
//import com.koro.app.translation.dto.TranslationResponse;
//import com.koro.app.translation.entity.Translation;
//import com.koro.app.translation.repository.TranslationRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//import java.util.LinkedHashMap;
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/v1")
//public class TranslationController {
//
//    @Autowired
//    private TranslationRepository translationRepository;
//
//    @Autowired
//    private LanguageRepository languageRepository;
//
//    @Autowired
//    private ConceptRepository conceptRepository;
//
//    @Autowired
//    private ActivityLogService activityLogService;
//
//    @GetMapping("/translations")
//    public ResponseEntity<List<TranslationResponse>> getTranslations(
//            @RequestParam(required = false) String conceptId,
//            @RequestParam(required = false) String languageId) {
//
//        List<Translation> results;
//        if (conceptId != null && languageId != null) {
//            results = translationRepository.findByConceptIdAndLanguageId(conceptId, languageId)
//                    .map(List::of)
//                    .orElse(List.of());
//        } else if (conceptId != null) {
//            results = translationRepository.findByConceptId(conceptId);
//        } else if (languageId != null) {
//            results = translationRepository.findByLanguageId(languageId);
//        } else {
//            results = translationRepository.findAll();
//        }
//
//        return ResponseEntity.ok(results.stream()
//                .filter(t -> t.getConcept() != null && t.getLanguage() != null)
//                .map(TranslationResponse::fromTranslation)
//                .collect(Collectors.toList()));
//    }
//
//    @PostMapping("/translations/search")
//    public ResponseEntity<?> searchTranslations(@RequestBody TranslationSearchRequest request) {
//        String query = TextNormalizer.normalize(request.getQuery());
//        if (query == null || query.isEmpty()) {
//            return ResponseEntity.badRequest().body("Query string is empty");
//        }
//
//        // Try the query both normalized and exactly as typed. Stored rows added before
//        // normalization was in place, or a client that already sends NFC, are still matched.
//        String rawQuery = request.getQuery().trim();
//        Set<String> variants = new LinkedHashSet<>();
//        variants.add(query);
//        if (!rawQuery.isEmpty()) {
//            variants.add(rawQuery);
//        }
//
//        String sourceLanguageId = request.getSourceLanguageId();
//        String targetLanguageId = request.getTargetLanguageId();
//
//        // De-duplicate matches by translation id while keeping the order they were found in.
//        Map<String, Translation> matches = new LinkedHashMap<>();
//
//        if (sourceLanguageId != null && targetLanguageId != null) {
//            // Cross-language: match a word in the source language, then return the
//            // corresponding entry in the target language for each concept found.
//            Set<String> conceptIds = new LinkedHashSet<>();
//            for (String variant : variants) {
//                collectConceptIdsFromTranslations(conceptIds,
//                        translationRepository.findByLanguageIdAndTextContainingIgnoreCase(sourceLanguageId, variant));
//                collectConceptIdsFromTranslations(conceptIds,
//                        translationRepository.findByLanguageIdAndPronunciationContainingIgnoreCase(sourceLanguageId, variant));
//            }
//            for (String conceptId : conceptIds) {
//                translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
//                        .ifPresent(t -> putMatch(matches, t));
//            }
//        } else if (targetLanguageId != null) {
//            // Search within a single language, across the word, its pronunciation and its concept.
//            Set<String> conceptIds = new LinkedHashSet<>();
//            for (String variant : variants) {
//                addMatches(matches,
//                        translationRepository.findByLanguageIdAndTextContainingIgnoreCase(targetLanguageId, variant));
//                addMatches(matches,
//                        translationRepository.findByLanguageIdAndPronunciationContainingIgnoreCase(targetLanguageId, variant));
//                collectConceptIdsFromConcepts(conceptIds, conceptRepository
//                        .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(variant, variant));
//            }
//            for (String conceptId : conceptIds) {
//                translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
//                        .ifPresent(t -> putMatch(matches, t));
//            }
//        } else {
//            // Global search across every language.
//            Set<String> conceptIds = new LinkedHashSet<>();
//            for (String variant : variants) {
//                addMatches(matches, translationRepository.findByTextContainingIgnoreCase(variant));
//                addMatches(matches, translationRepository.findByPronunciationContainingIgnoreCase(variant));
//                collectConceptIdsFromConcepts(conceptIds, conceptRepository
//                        .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(variant, variant));
//            }
//            if (!conceptIds.isEmpty()) {
//                addMatches(matches, translationRepository.findByConceptIdIn(conceptIds));
//            }
//        }
//
//        // Log translation activity
//        activityLogService.log(
//                ActivityType.TRANSLATION,
//                "Searched translations for: " + query,
//                null,
//                "sourceLang=" + sourceLanguageId + ", targetLang=" + targetLanguageId
//        );
//
//        return ResponseEntity.ok(matches.values().stream()
//                .filter(t -> t.getConcept() != null && t.getLanguage() != null)
//                .map(TranslationResponse::fromTranslation)
//                .collect(Collectors.toList()));
//    }
//
//    private static void addMatches(Map<String, Translation> sink, List<Translation> found) {
//        for (Translation t : found) {
//            putMatch(sink, t);
//        }
//    }
//
//    private static void putMatch(Map<String, Translation> sink, Translation t) {
//        if (t.getId() != null) {
//            sink.putIfAbsent(t.getId(), t);
//        }
//    }
//
//    private static void collectConceptIdsFromTranslations(Set<String> sink, List<Translation> found) {
//        for (Translation t : found) {
//            if (t.getConcept() != null && t.getConcept().getId() != null) {
//                sink.add(t.getConcept().getId());
//            }
//        }
//    }
//
//    private static void collectConceptIdsFromConcepts(Set<String> sink, List<Concept> found) {
//        for (Concept c : found) {
//            if (c.getId() != null) {
//                sink.add(c.getId());
//            }
//        }
//    }
//
//    @PostMapping("/admin/translations")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<?> createTranslation(@RequestBody TranslationRequest request) {
//        Concept concept = conceptRepository.findById(request.getConceptId())
//                .orElseThrow(() -> new RuntimeException("Concept not found"));
//        Language language = languageRepository.findById(request.getLanguageId())
//                .orElseThrow(() -> new RuntimeException("Language not found"));
//
//        // check if unique constraint violated, if so update existing
//        Translation translation = translationRepository.findByConceptIdAndLanguageId(concept.getId(), language.getId())
//                .orElse(new Translation());
//
//        translation.setConcept(concept);
//        translation.setLanguage(language);
//        translation.setText(TextNormalizer.normalize(request.getText()));
//        translation.setPronunciation(TextNormalizer.normalize(request.getPronunciation()));
//        translation.setNotes(request.getNotes());
//        translation.setVerified(true); // admin entered is verified by default
//
//        Translation saved = translationRepository.save(translation);
//        activityLogService.log(ActivityType.ADD_VOCABULARY, "Created translation for Concept " + concept.getName() + " in " + language.getName(), saved.getId(), null);
//
//        return ResponseEntity.ok(TranslationResponse.fromTranslation(saved));
//    }
//
//    @PutMapping("/admin/translations/{id}")
//    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
//    public ResponseEntity<?> updateTranslation(@PathVariable String id, @RequestBody TranslationRequest request) {
//        return translationRepository.findById(id)
//                .map(translation -> {
//                    if (request.getConceptId() != null) {
//                        Concept concept = conceptRepository.findById(request.getConceptId())
//                                .orElseThrow(() -> new RuntimeException("Concept not found"));
//                        translation.setConcept(concept);
//                    }
//                    if (request.getLanguageId() != null) {
//                        Language language = languageRepository.findById(request.getLanguageId())
//                                .orElseThrow(() -> new RuntimeException("Language not found"));
//                        translation.setLanguage(language);
//                    }
//                    if (request.getText() != null) translation.setText(TextNormalizer.normalize(request.getText()));
//                    if (request.getPronunciation() != null) translation.setPronunciation(TextNormalizer.normalize(request.getPronunciation()));
//                    if (request.getNotes() != null) translation.setNotes(request.getNotes());
//
//                    Translation saved = translationRepository.save(translation);
//                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Updated translation for Concept " + saved.getConcept().getName() + " in " + saved.getLanguage().getName(), saved.getId(), null);
//                    return ResponseEntity.ok(TranslationResponse.fromTranslation(saved));
//                })
//                .orElseGet(() -> ResponseEntity.notFound().build());
//    }
//
//    @DeleteMapping("/admin/translations/{id}")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<?> deleteTranslation(@PathVariable String id) {
//        return translationRepository.findById(id)
//                .map(translation -> {
//                    translationRepository.delete(translation);
//                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Deleted translation " + translation.getId(), translation.getId(), null);
//                    return ResponseEntity.ok(new MessageResponse("Translation deleted successfully!"));
//                })
//                .orElseGet(() -> ResponseEntity.notFound().build());
//    }
//}
//
//// Request helper inside same file
//class TranslationRequest {
//    private String conceptId;
//    private String languageId;
//    private String text;
//    private String pronunciation;
//    private String notes;
//
//    public String getConceptId() { return conceptId; }
//    public void setConceptId(String conceptId) { this.conceptId = conceptId; }
//    public String getLanguageId() { return languageId; }
//    public void setLanguageId(String languageId) { this.languageId = languageId; }
//    public String getText() { return text; }
//    public void setText(String text) { this.text = text; }
//    public String getPronunciation() { return pronunciation; }
//    public void setPronunciation(String pronunciation) { this.pronunciation = pronunciation; }
//    public String getNotes() { return notes; }
//    public void setNotes(String notes) { this.notes = notes; }
//}


package com.koro.app.translation.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.TranslationResponse;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class TranslationController {

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private ActivityLogService activityLogService;

    /*
     * ============================================================
     * IN-MEMORY SEARCH INDEX
     * ============================================================
     */

    private final Map<String, List<Translation>> textIndex =
            new ConcurrentHashMap<>();

    private final Map<String, List<Translation>> pronunciationIndex =
            new ConcurrentHashMap<>();

    private List<Translation> globalTextIndex = List.of();

    private List<Translation> globalPronunciationIndex = List.of();

    /*
     * ============================================================
     * INITIALIZE SEARCH INDEX
     * ============================================================
     */

    @jakarta.annotation.PostConstruct
    public void initializeSearchIndex() {
        rebuildSearchIndex();
    }

    /*
     * ============================================================
     * REBUILD INDEX
     * ============================================================
     */

    public synchronized void rebuildSearchIndex() {

        List<Translation> allTranslations =
                translationRepository.findAll();

        /*
         * Global text index
         */
        globalTextIndex = allTranslations.stream()
                .filter(t -> t.getText() != null)
                .sorted(
                        Comparator.comparing(
                                t -> normalizeForSearch(t.getText())
                        )
                )
                .toList();

        /*
         * Global pronunciation index
         */
        globalPronunciationIndex = allTranslations.stream()
                .filter(t -> t.getPronunciation() != null)
                .sorted(
                        Comparator.comparing(
                                t -> normalizeForSearch(t.getPronunciation())
                        )
                )
                .toList();

        /*
         * Language-specific indexes
         */
        Map<String, List<Translation>> newTextIndex =
                new ConcurrentHashMap<>();

        Map<String, List<Translation>> newPronunciationIndex =
                new ConcurrentHashMap<>();

        for (Translation translation : allTranslations) {

            if (translation.getLanguage() == null ||
                    translation.getLanguage().getId() == null) {
                continue;
            }

            String languageId =
                    translation.getLanguage().getId();

            /*
             * Text
             */
            if (translation.getText() != null &&
                    !translation.getText().isBlank()) {

                newTextIndex
                        .computeIfAbsent(
                                languageId,
                                key -> new ArrayList<>()
                        )
                        .add(translation);
            }

            /*
             * Pronunciation
             */
            if (translation.getPronunciation() != null &&
                    !translation.getPronunciation().isBlank()) {

                newPronunciationIndex
                        .computeIfAbsent(
                                languageId,
                                key -> new ArrayList<>()
                        )
                        .add(translation);
            }
        }

        /*
         * Sort every language index.
         */
        newTextIndex.values().forEach(
                list -> list.sort(
                        Comparator.comparing(
                                t -> normalizeForSearch(t.getText())
                        )
                )
        );

        newPronunciationIndex.values().forEach(
                list -> list.sort(
                        Comparator.comparing(
                                t -> normalizeForSearch(
                                        t.getPronunciation()
                                )
                        )
                )
        );

        textIndex.clear();
        textIndex.putAll(newTextIndex);

        pronunciationIndex.clear();
        pronunciationIndex.putAll(newPronunciationIndex);
    }

    /*
     * ============================================================
     * GET TRANSLATIONS
     * ============================================================
     */

    @GetMapping("/translations/count")
    public ResponseEntity<Long> getTranslationsCount() {
        return ResponseEntity.ok(translationRepository.count());
    }

    @GetMapping("/translations")
    public ResponseEntity<?> getTranslations(
            @RequestParam(required = false) String conceptId,
            @RequestParam(required = false) String languageId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page != null && size != null) {
            org.springframework.data.domain.Pageable pageable = 
                    org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<Translation> results;

            if (conceptId != null && languageId != null) {
                List<Translation> list = translationRepository
                        .findByConceptIdAndLanguageId(conceptId, languageId)
                        .map(List::of)
                        .orElse(List.of());
                results = new org.springframework.data.domain.PageImpl<>(list, pageable, list.size());
            } else if (conceptId != null) {
                results = translationRepository.findByConceptId(conceptId, pageable);
            } else if (languageId != null) {
                results = translationRepository.findByLanguageId(languageId, pageable);
            } else {
                results = translationRepository.findAll(pageable);
            }

            List<TranslationResponse> content = results.getContent().stream()
                    .filter(t -> t.getConcept() != null && t.getLanguage() != null)
                    .map(TranslationResponse::fromTranslation)
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("content", content);
            response.put("page", results.getNumber());
            response.put("size", results.getSize());
            response.put("totalElements", results.getTotalElements());
            response.put("totalPages", results.getTotalPages());
            response.put("hasNext", results.hasNext());
            response.put("hasPrevious", results.hasPrevious());
            return ResponseEntity.ok(response);
        } else {
            List<Translation> results;

            if (conceptId != null && languageId != null) {
                results = translationRepository
                        .findByConceptIdAndLanguageId(conceptId, languageId)
                        .map(List::of)
                        .orElse(List.of());
            } else if (conceptId != null) {
                results = translationRepository.findByConceptId(conceptId);
            } else if (languageId != null) {
                results = translationRepository.findByLanguageId(languageId);
            } else {
                results = translationRepository.findAll();
            }

            return ResponseEntity.ok(
                    results.stream()
                            .filter(t -> t.getConcept() != null && t.getLanguage() != null)
                            .map(TranslationResponse::fromTranslation)
                            .collect(Collectors.toList())
            );
        }
    }

    /*
     * ============================================================
     * SEARCH
     * ============================================================
     */

    @PostMapping("/translations/search")
    public ResponseEntity<?> searchTranslations(
            @RequestBody TranslationSearchRequest request) {

        String query = TextNormalizer.normalize(
                request.getQuery()
        );

        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Query string is empty");
        }

        query = normalizeForSearch(query);

        String sourceLanguageId =
                request.getSourceLanguageId();

        String targetLanguageId =
                request.getTargetLanguageId();

        /*
         * LinkedHashMap keeps results unique while preserving order.
         */
        Map<String, Translation> matches =
                new LinkedHashMap<>();

        /*
         * ========================================================
         * CROSS LANGUAGE SEARCH
         * ========================================================
         *
         * Example:
         *
         * English -> Bangla
         *
         * Search "hello"
         *
         * 1. Binary search English
         * 2. Find matching concepts
         * 3. Get Bangla translation
         */
        if (sourceLanguageId != null &&
                targetLanguageId != null) {

            Set<String> conceptIds =
                    new LinkedHashSet<>();

            List<Translation> sourceTextMatches =
                    binaryPrefixSearch(
                            textIndex.getOrDefault(
                                    sourceLanguageId,
                                    List.of()
                            ),
                            query,
                            SearchField.TEXT
                    );

            collectConceptIdsFromTranslations(
                    conceptIds,
                    sourceTextMatches
            );

            List<Translation> sourcePronunciationMatches =
                    binaryPrefixSearch(
                            pronunciationIndex.getOrDefault(
                                    sourceLanguageId,
                                    List.of()
                            ),
                            query,
                            SearchField.PRONUNCIATION
                    );

            collectConceptIdsFromTranslations(
                    conceptIds,
                    sourcePronunciationMatches
            );

            /*
             * Return target-language translations.
             */
            for (String conceptId : conceptIds) {

                translationRepository
                        .findByConceptIdAndLanguageId(
                                conceptId,
                                targetLanguageId
                        )
                        .ifPresent(
                                translation ->
                                        putMatch(
                                                matches,
                                                translation
                                        )
                        );
            }

        }

        /*
         * ========================================================
         * TARGET LANGUAGE SEARCH
         * ========================================================
         */
        else if (targetLanguageId != null) {

            List<Translation> languageText =
                    textIndex.getOrDefault(
                            targetLanguageId,
                            List.of()
                    );

            List<Translation> languagePronunciation =
                    pronunciationIndex.getOrDefault(
                            targetLanguageId,
                            List.of()
                    );

            /*
             * Binary search text.
             */
            addMatches(
                    matches,
                    binaryPrefixSearch(
                            languageText,
                            query,
                            SearchField.TEXT
                    )
            );

            /*
             * Binary search pronunciation.
             */
            addMatches(
                    matches,
                    binaryPrefixSearch(
                            languagePronunciation,
                            query,
                            SearchField.PRONUNCIATION
                    )
            );

            /*
             * Concept search.
             *
             * This remains a database query because concepts are
             * separate entities and are not part of our text index.
             */
            List<Concept> concepts =
                    conceptRepository
                            .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                    query,
                                    query
                            );

            Set<String> conceptIds =
                    new LinkedHashSet<>();

            collectConceptIdsFromConcepts(
                    conceptIds,
                    concepts
            );

            for (String conceptId : conceptIds) {

                translationRepository
                        .findByConceptIdAndLanguageId(
                                conceptId,
                                targetLanguageId
                        )
                        .ifPresent(
                                translation ->
                                        putMatch(
                                                matches,
                                                translation
                                        )
                        );
            }

        }

        /*
         * ========================================================
         * GLOBAL SEARCH
         * ========================================================
         */
        else {

            /*
             * Binary search global text.
             */
            addMatches(
                    matches,
                    binaryPrefixSearch(
                            globalTextIndex,
                            query,
                            SearchField.TEXT
                    )
            );

            /*
             * Binary search global pronunciation.
             */
            addMatches(
                    matches,
                    binaryPrefixSearch(
                            globalPronunciationIndex,
                            query,
                            SearchField.PRONUNCIATION
                    )
            );

            /*
             * Concept search.
             */
            List<Concept> concepts =
                    conceptRepository
                            .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                    query,
                                    query
                            );

            Set<String> conceptIds =
                    new LinkedHashSet<>();

            collectConceptIdsFromConcepts(
                    conceptIds,
                    concepts
            );

            if (!conceptIds.isEmpty()) {

                addMatches(
                        matches,
                        translationRepository
                                .findByConceptIdIn(conceptIds)
                );
            }
        }

        /*
         * Activity log.
         */
        activityLogService.log(
                ActivityType.TRANSLATION,
                "Searched translations for: " + query,
                null,
                "sourceLang=" + sourceLanguageId +
                        ", targetLang=" + targetLanguageId
        );

        /*
         * Response.
         */
        return ResponseEntity.ok(
                matches.values()
                        .stream()
                        .filter(t ->
                                t.getConcept() != null &&
                                        t.getLanguage() != null
                        )
                        .map(TranslationResponse::fromTranslation)
                        .collect(Collectors.toList())
        );
    }

    /*
     * ============================================================
     * BINARY PREFIX SEARCH
     * ============================================================
     *
     * Finds the first matching item using binary search.
     *
     * Example sorted list:
     *
     * apple
     * application
     * banana
     * car
     * card
     * careful
     * dog
     *
     * query = "car"
     *
     * Binary search jumps directly near:
     *
     * car
     *
     * Then we scan forward only while the prefix matches.
     */
    private List<Translation> binaryPrefixSearch(
            List<Translation> sortedList,
            String query,
            SearchField field) {

        if (sortedList == null ||
                sortedList.isEmpty() ||
                query == null ||
                query.isEmpty()) {

            return List.of();
        }

        int left = 0;
        int right = sortedList.size() - 1;

        int firstMatch = -1;

        /*
         * Find first position where value >= query.
         */
        while (left <= right) {

            int middle =
                    left + (right - left) / 2;

            String value =
                    getSearchValue(
                            sortedList.get(middle),
                            field
                    );

            if (value == null) {
                left = middle + 1;
                continue;
            }

            int comparison =
                    value.compareTo(query);

            if (comparison >= 0) {

                firstMatch = middle;
                right = middle - 1;

            } else {

                left = middle + 1;
            }
        }

        if (firstMatch == -1) {
            return List.of();
        }

        /*
         * Collect only matching prefix entries.
         */
        List<Translation> results =
                new ArrayList<>();

        for (int i = firstMatch;
             i < sortedList.size();
             i++) {

            Translation translation =
                    sortedList.get(i);

            String value =
                    getSearchValue(
                            translation,
                            field
                    );

            if (value == null) {
                continue;
            }

            if (value.startsWith(query)) {

                results.add(translation);

            } else if (value.compareTo(query) > 0) {

                /*
                 * Since the list is sorted, once we pass
                 * the prefix range, we can stop.
                 */
                break;
            }
        }

        return results;
    }

    /*
     * ============================================================
     * SEARCH VALUE
     * ============================================================
     */

    private String getSearchValue(
            Translation translation,
            SearchField field) {

        if (translation == null) {
            return "";
        }

        if (field == SearchField.TEXT) {

            return normalizeForSearch(
                    translation.getText()
            );

        } else {

            return normalizeForSearch(
                    translation.getPronunciation()
            );
        }
    }

    /*
     * ============================================================
     * NORMALIZATION
     * ============================================================
     */

    private static String normalizeForSearch(
            String value) {

        if (value == null) {
            return "";
        }

        return TextNormalizer
                .normalize(value)
                .trim()
                .toLowerCase();
    }

    /*
     * ============================================================
     * MATCH HELPERS
     * ============================================================
     */

    private static void addMatches(
            Map<String, Translation> sink,
            List<Translation> found) {

        if (found == null) {
            return;
        }

        for (Translation translation : found) {
            putMatch(sink, translation);
        }
    }

    private static void putMatch(
            Map<String, Translation> sink,
            Translation translation) {

        if (translation != null &&
                translation.getId() != null) {

            sink.putIfAbsent(
                    translation.getId(),
                    translation
            );
        }
    }

    private static void collectConceptIdsFromTranslations(
            Set<String> sink,
            List<Translation> found) {

        if (found == null) {
            return;
        }

        for (Translation translation : found) {

            if (translation.getConcept() != null &&
                    translation.getConcept().getId() != null) {

                sink.add(
                        translation
                                .getConcept()
                                .getId()
                );
            }
        }
    }

    private static void collectConceptIdsFromConcepts(
            Set<String> sink,
            List<Concept> found) {

        if (found == null) {
            return;
        }

        for (Concept concept : found) {

            if (concept.getId() != null) {
                sink.add(concept.getId());
            }
        }
    }

    /*
     * ============================================================
     * ADMIN: CREATE
     * ============================================================
     */

    @PostMapping("/admin/translations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTranslation(
            @RequestBody TranslationRequest request) {

        Concept concept =
                conceptRepository
                        .findById(request.getConceptId())
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Concept not found"
                                )
                        );

        Language language =
                languageRepository
                        .findById(request.getLanguageId())
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Language not found"
                                )
                        );

        Translation translation =
                translationRepository
                        .findByConceptIdAndLanguageId(
                                concept.getId(),
                                language.getId()
                        )
                        .orElse(new Translation());

        translation.setConcept(concept);
        translation.setLanguage(language);

        translation.setText(
                TextNormalizer.normalize(
                        request.getText()
                )
        );

        translation.setPronunciation(
                TextNormalizer.normalize(
                        request.getPronunciation()
                )
        );

        translation.setNotes(request.getNotes());

        translation.setVerified(true);

        Translation saved =
                translationRepository.save(
                        translation
                );

        /*
         * IMPORTANT:
         * Database changed -> rebuild binary-search index.
         */
        rebuildSearchIndex();

        activityLogService.log(
                ActivityType.ADD_VOCABULARY,
                "Created translation for Concept " +
                        concept.getName() +
                        " in " +
                        language.getName(),
                saved.getId(),
                null
        );

        return ResponseEntity.ok(
                TranslationResponse.fromTranslation(
                        saved
                )
        );
    }

    /*
     * ============================================================
     * ADMIN: UPDATE
     * ============================================================
     */

    @PutMapping("/admin/translations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> updateTranslation(
            @PathVariable String id,
            @RequestBody TranslationRequest request) {

        return translationRepository
                .findById(id)
                .map(translation -> {

                    if (request.getConceptId() != null) {

                        Concept concept =
                                conceptRepository
                                        .findById(
                                                request.getConceptId()
                                        )
                                        .orElseThrow(
                                                () -> new RuntimeException(
                                                        "Concept not found"
                                                )
                                        );

                        translation.setConcept(
                                concept
                        );
                    }

                    if (request.getLanguageId() != null) {

                        Language language =
                                languageRepository
                                        .findById(
                                                request.getLanguageId()
                                        )
                                        .orElseThrow(
                                                () -> new RuntimeException(
                                                        "Language not found"
                                                )
                                        );

                        translation.setLanguage(
                                language
                        );
                    }

                    if (request.getText() != null) {

                        translation.setText(
                                TextNormalizer.normalize(
                                        request.getText()
                                )
                        );
                    }

                    if (request.getPronunciation() != null) {

                        translation.setPronunciation(
                                TextNormalizer.normalize(
                                        request.getPronunciation()
                                )
                        );
                    }

                    if (request.getNotes() != null) {
                        translation.setNotes(
                                request.getNotes()
                        );
                    }

                    Translation saved =
                            translationRepository.save(
                                    translation
                            );

                    /*
                     * Rebuild index after modification.
                     */
                    rebuildSearchIndex();

                    activityLogService.log(
                            ActivityType.ADD_VOCABULARY,
                            "Updated translation for Concept " +
                                    saved.getConcept().getName() +
                                    " in " +
                                    saved.getLanguage().getName(),
                            saved.getId(),
                            null
                    );

                    return ResponseEntity.ok(
                            TranslationResponse.fromTranslation(
                                    saved
                            )
                    );

                })
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }

    /*
     * ============================================================
     * ADMIN: DELETE
     * ============================================================
     */

    @DeleteMapping("/admin/translations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTranslation(
            @PathVariable String id) {

        return translationRepository
                .findById(id)
                .map(translation -> {

                    translationRepository.delete(
                            translation
                    );

                    /*
                     * Remove deleted translation from
                     * binary-search index.
                     */
                    rebuildSearchIndex();

                    activityLogService.log(
                            ActivityType.ADD_VOCABULARY,
                            "Deleted translation " +
                                    translation.getId(),
                            translation.getId(),
                            null
                    );

                    return ResponseEntity.ok(
                            new MessageResponse(
                                    "Translation deleted successfully!"
                            )
                    );
                })
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }

    /*
     * ============================================================
     * SEARCH FIELD
     * ============================================================
     */

    private enum SearchField {
        TEXT,
        PRONUNCIATION
    }
}


/*
 * ================================================================
 * REQUEST DTO
 * ================================================================
 */

class TranslationRequest {

    private String conceptId;
    private String languageId;
    private String text;
    private String pronunciation;
    private String notes;

    public String getConceptId() {
        return conceptId;
    }

    public void setConceptId(String conceptId) {
        this.conceptId = conceptId;
    }

    public String getLanguageId() {
        return languageId;
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getPronunciation() {
        return pronunciation;
    }

    public void setPronunciation(String pronunciation) {
        this.pronunciation = pronunciation;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}


/*
 * ================================================================
 * SEARCH REQUEST
 * ================================================================
 *
 * If you already have TranslationSearchRequest.java,
 * keep using that existing DTO and remove this class.
 */
class TranslationSearchRequest {

    private String query;
    private String sourceLanguageId;
    private String targetLanguageId;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSourceLanguageId() {
        return sourceLanguageId;
    }

    public void setSourceLanguageId(
            String sourceLanguageId) {

        this.sourceLanguageId = sourceLanguageId;
    }

    public String getTargetLanguageId() {
        return targetLanguageId;
    }

    public void setTargetLanguageId(
            String targetLanguageId) {

        this.targetLanguageId = targetLanguageId;
    }
}
