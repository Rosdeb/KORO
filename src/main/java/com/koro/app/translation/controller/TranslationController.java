package com.koro.app.translation.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.TranslationSearchRequest;
import com.koro.app.translation.dto.TranslationResponse;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @GetMapping("/translations")
    public ResponseEntity<List<TranslationResponse>> getTranslations(
            @RequestParam(required = false) String conceptId,
            @RequestParam(required = false) String languageId) {
        
        List<Translation> results;
        if (conceptId != null && languageId != null) {
            results = translationRepository.findByConceptIdAndLanguageId(conceptId, languageId)
                    .map(List::of)
                    .orElse(List.of());
        } else if (conceptId != null) {
            results = translationRepository.findByConceptId(conceptId);
        } else if (languageId != null) {
            results = translationRepository.findByLanguageId(languageId);
        } else {
            results = translationRepository.findAll();
        }

        return ResponseEntity.ok(results.stream()
                .filter(t -> t.getConcept() != null && t.getLanguage() != null)
                .map(TranslationResponse::fromTranslation)
                .collect(Collectors.toList()));
    }

    @PostMapping("/translations/search")
    public ResponseEntity<?> searchTranslations(@RequestBody TranslationSearchRequest request) {
        String query = TextNormalizer.normalize(request.getQuery());
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().body("Query string is empty");
        }

        // Try the query both normalized and exactly as typed. Stored rows added before
        // normalization was in place, or a client that already sends NFC, are still matched.
        String rawQuery = request.getQuery().trim();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(query);
        if (!rawQuery.isEmpty()) {
            variants.add(rawQuery);
        }

        String sourceLanguageId = request.getSourceLanguageId();
        String targetLanguageId = request.getTargetLanguageId();

        // De-duplicate matches by translation id while keeping the order they were found in.
        Map<String, Translation> matches = new LinkedHashMap<>();

        if (sourceLanguageId != null && targetLanguageId != null) {
            // Cross-language: match a word in the source language, then return the
            // corresponding entry in the target language for each concept found.
            Set<String> conceptIds = new LinkedHashSet<>();
            for (String variant : variants) {
                collectConceptIdsFromTranslations(conceptIds,
                        translationRepository.findByLanguageIdAndTextContainingIgnoreCase(sourceLanguageId, variant));
                collectConceptIdsFromTranslations(conceptIds,
                        translationRepository.findByLanguageIdAndPronunciationContainingIgnoreCase(sourceLanguageId, variant));
            }
            for (String conceptId : conceptIds) {
                translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
                        .ifPresent(t -> putMatch(matches, t));
            }
        } else if (targetLanguageId != null) {
            // Search within a single language, across the word, its pronunciation and its concept.
            Set<String> conceptIds = new LinkedHashSet<>();
            for (String variant : variants) {
                addMatches(matches,
                        translationRepository.findByLanguageIdAndTextContainingIgnoreCase(targetLanguageId, variant));
                addMatches(matches,
                        translationRepository.findByLanguageIdAndPronunciationContainingIgnoreCase(targetLanguageId, variant));
                collectConceptIdsFromConcepts(conceptIds, conceptRepository
                        .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(variant, variant));
            }
            for (String conceptId : conceptIds) {
                translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
                        .ifPresent(t -> putMatch(matches, t));
            }
        } else {
            // Global search across every language.
            Set<String> conceptIds = new LinkedHashSet<>();
            for (String variant : variants) {
                addMatches(matches, translationRepository.findByTextContainingIgnoreCase(variant));
                addMatches(matches, translationRepository.findByPronunciationContainingIgnoreCase(variant));
                collectConceptIdsFromConcepts(conceptIds, conceptRepository
                        .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(variant, variant));
            }
            if (!conceptIds.isEmpty()) {
                addMatches(matches, translationRepository.findByConceptIdIn(conceptIds));
            }
        }

        // Log translation activity
        activityLogService.log(
                ActivityType.TRANSLATION,
                "Searched translations for: " + query,
                null,
                "sourceLang=" + sourceLanguageId + ", targetLang=" + targetLanguageId
        );

        return ResponseEntity.ok(matches.values().stream()
                .filter(t -> t.getConcept() != null && t.getLanguage() != null)
                .map(TranslationResponse::fromTranslation)
                .collect(Collectors.toList()));
    }

    private static void addMatches(Map<String, Translation> sink, List<Translation> found) {
        for (Translation t : found) {
            putMatch(sink, t);
        }
    }

    private static void putMatch(Map<String, Translation> sink, Translation t) {
        if (t.getId() != null) {
            sink.putIfAbsent(t.getId(), t);
        }
    }

    private static void collectConceptIdsFromTranslations(Set<String> sink, List<Translation> found) {
        for (Translation t : found) {
            if (t.getConcept() != null && t.getConcept().getId() != null) {
                sink.add(t.getConcept().getId());
            }
        }
    }

    private static void collectConceptIdsFromConcepts(Set<String> sink, List<Concept> found) {
        for (Concept c : found) {
            if (c.getId() != null) {
                sink.add(c.getId());
            }
        }
    }

    @PostMapping("/admin/translations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTranslation(@RequestBody TranslationRequest request) {
        Concept concept = conceptRepository.findById(request.getConceptId())
                .orElseThrow(() -> new RuntimeException("Concept not found"));
        Language language = languageRepository.findById(request.getLanguageId())
                .orElseThrow(() -> new RuntimeException("Language not found"));

        // check if unique constraint violated, if so update existing
        Translation translation = translationRepository.findByConceptIdAndLanguageId(concept.getId(), language.getId())
                .orElse(new Translation());

        translation.setConcept(concept);
        translation.setLanguage(language);
        translation.setText(TextNormalizer.normalize(request.getText()));
        translation.setPronunciation(TextNormalizer.normalize(request.getPronunciation()));
        translation.setNotes(request.getNotes());
        translation.setVerified(true); // admin entered is verified by default

        Translation saved = translationRepository.save(translation);
        activityLogService.log(ActivityType.ADD_VOCABULARY, "Created translation for Concept " + concept.getName() + " in " + language.getName(), saved.getId(), null);

        return ResponseEntity.ok(TranslationResponse.fromTranslation(saved));
    }

    @PutMapping("/admin/translations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<?> updateTranslation(@PathVariable String id, @RequestBody TranslationRequest request) {
        return translationRepository.findById(id)
                .map(translation -> {
                    if (request.getConceptId() != null) {
                        Concept concept = conceptRepository.findById(request.getConceptId())
                                .orElseThrow(() -> new RuntimeException("Concept not found"));
                        translation.setConcept(concept);
                    }
                    if (request.getLanguageId() != null) {
                        Language language = languageRepository.findById(request.getLanguageId())
                                .orElseThrow(() -> new RuntimeException("Language not found"));
                        translation.setLanguage(language);
                    }
                    if (request.getText() != null) translation.setText(TextNormalizer.normalize(request.getText()));
                    if (request.getPronunciation() != null) translation.setPronunciation(TextNormalizer.normalize(request.getPronunciation()));
                    if (request.getNotes() != null) translation.setNotes(request.getNotes());

                    Translation saved = translationRepository.save(translation);
                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Updated translation for Concept " + saved.getConcept().getName() + " in " + saved.getLanguage().getName(), saved.getId(), null);
                    return ResponseEntity.ok(TranslationResponse.fromTranslation(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/translations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTranslation(@PathVariable String id) {
        return translationRepository.findById(id)
                .map(translation -> {
                    translationRepository.delete(translation);
                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Deleted translation " + translation.getId(), translation.getId(), null);
                    return ResponseEntity.ok(new MessageResponse("Translation deleted successfully!"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

// Request helper inside same file
class TranslationRequest {
    private String conceptId;
    private String languageId;
    private String text;
    private String pronunciation;
    private String notes;

    public String getConceptId() { return conceptId; }
    public void setConceptId(String conceptId) { this.conceptId = conceptId; }
    public String getLanguageId() { return languageId; }
    public void setLanguageId(String languageId) { this.languageId = languageId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getPronunciation() { return pronunciation; }
    public void setPronunciation(String pronunciation) { this.pronunciation = pronunciation; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
