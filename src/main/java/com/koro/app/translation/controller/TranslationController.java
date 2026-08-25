package com.koro.app.translation.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
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
import java.util.ArrayList;
import java.util.List;
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
                .map(TranslationResponse::fromTranslation)
                .collect(Collectors.toList()));
    }

    @PostMapping("/translations/search")
    public ResponseEntity<?> searchTranslations(@RequestBody TranslationSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Query string is empty");
        }

        List<Translation> results;

        if (request.getSourceLanguageId() != null && request.getTargetLanguageId() != null) {
            // Programmatic cross-language translation in MongoDB
            List<Translation> sourceTranslations = translationRepository.findByLanguageIdAndTextContainingIgnoreCase(
                    request.getSourceLanguageId(), 
                    request.getQuery()
            );
            List<String> conceptIds = sourceTranslations.stream()
                    .map(t -> t.getConcept().getId())
                    .collect(Collectors.toList());
            
            results = new ArrayList<>();
            for (String conceptId : conceptIds) {
                translationRepository.findByConceptIdAndLanguageId(conceptId, request.getTargetLanguageId())
                        .ifPresent(results::add);
            }
        } else if (request.getTargetLanguageId() != null) {
            // search translations in target language matching text
            results = translationRepository.findByLanguageIdAndTextContainingIgnoreCase(
                    request.getTargetLanguageId(), 
                    request.getQuery()
            );
        } else {
            // global query matching translation text
            results = translationRepository.findByTextContainingIgnoreCase(request.getQuery());
        }

        // Log translation activity
        activityLogService.log(
                ActivityType.TRANSLATION, 
                "Searched translations for: " + request.getQuery(), 
                null, 
                "sourceLang=" + request.getSourceLanguageId() + ", targetLang=" + request.getTargetLanguageId()
        );

        return ResponseEntity.ok(results.stream()
                .map(TranslationResponse::fromTranslation)
                .collect(Collectors.toList()));
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
        translation.setText(request.getText());
        translation.setPronunciation(request.getPronunciation());
        translation.setNotes(request.getNotes());
        translation.setVerified(true); // admin entered is verified by default

        Translation saved = translationRepository.save(translation);
        activityLogService.log(ActivityType.ADD_VOCABULARY, "Created translation for Concept " + concept.getName() + " in " + language.getName(), saved.getId(), null);

        return ResponseEntity.ok(TranslationResponse.fromTranslation(saved));
    }

    @PutMapping("/admin/translations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
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
                    if (request.getText() != null) translation.setText(request.getText());
                    if (request.getPronunciation() != null) translation.setPronunciation(request.getPronunciation());
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
