package com.koro.app.submission.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.entity.Category;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.submission.dto.SubmissionRequest;
import com.koro.app.submission.dto.SubmissionReviewRequest;
import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.entity.TranslationSubmission;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class SubmissionController {

    private static final String BANGLA_CODE = "bn";
    private static final String ENGLISH_CODE = "en";

    @Autowired
    private TranslationSubmissionRepository submissionRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

    // --- User Submission endpoints ---
    @PostMapping("/submissions")
    public ResponseEntity<?> submitTranslation(@Valid @RequestBody List<SubmissionRequest> requests) {
        User user = getCurrentUser();
        List<TranslationSubmission> savedSubmissions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        List<SubmissionRequest> expandedRequests = new ArrayList<>();
        for (SubmissionRequest req : requests) {
            String sourceWord = req.getSourceWord();
            if (sourceWord != null && sourceWord.contains("/")) {
                String[] sources = sourceWord.split("/");
                String[] banglas = req.getBanglaTranslation() != null ? req.getBanglaTranslation().split("/") : new String[0];
                String[] englishes = req.getEnglishTranslation() != null ? req.getEnglishTranslation().split("/") : new String[0];
                String[] pronunciations = req.getPronunciation() != null ? req.getPronunciation().split("/") : new String[0];

                for (int i = 0; i < sources.length; i++) {
                    SubmissionRequest splitReq = new SubmissionRequest();
                    splitReq.setCategoryId(req.getCategoryId());
                    splitReq.setSourceLanguageId(req.getSourceLanguageId());
                    splitReq.setExampleSentence(req.getExampleSentence());
                    splitReq.setNote(req.getNote());
                    
                    splitReq.setSourceWord(sources[i].trim());
                    String b = i < banglas.length ? banglas[i].trim() : (banglas.length > 0 ? banglas[banglas.length - 1].trim() : "");
                    splitReq.setBanglaTranslation(b);
                    String e = i < englishes.length ? englishes[i].trim() : (englishes.length > 0 ? englishes[englishes.length - 1].trim() : "");
                    splitReq.setEnglishTranslation(e);
                    String p = i < pronunciations.length ? pronunciations[i].trim() : (pronunciations.length > 0 ? pronunciations[pronunciations.length - 1].trim() : "");
                    splitReq.setPronunciation(p);
                    
                    expandedRequests.add(splitReq);
                }
            } else {
                expandedRequests.add(req);
            }
        }

        for (SubmissionRequest request : expandedRequests) {
            try {
                Category category = categoryRepository.findById(request.getCategoryId())
                        .orElseThrow(() -> new RuntimeException("Category not found for word: " + request.getSourceWord()));
                Language sourceLanguage = languageRepository.findById(request.getSourceLanguageId())
                        .orElseThrow(() -> new RuntimeException("Source language not found for word: " + request.getSourceWord()));

                String sourceWord = TextNormalizer.normalize(request.getSourceWord());

                boolean existsInDictionary = !translationRepository
                        .findByLanguageIdAndTextIgnoreCase(sourceLanguage.getId(), sourceWord).isEmpty();
                boolean alreadySubmitted = !submissionRepository
                        .findBySourceLanguageIdAndSourceWordIgnoreCaseAndStatusNot(
                                sourceLanguage.getId(), sourceWord, SubmissionStatus.REJECTED)
                        .isEmpty();

                if (existsInDictionary || alreadySubmitted) {
                    errors.add("Word already exists or submitted: " + sourceWord);
                    continue;
                }

                TranslationSubmission submission = TranslationSubmission.builder()
                        .category(category)
                        .sourceLanguage(sourceLanguage)
                        .sourceWord(sourceWord)
                        .banglaTranslation(request.getBanglaTranslation())
                        .englishTranslation(request.getEnglishTranslation())
                        .pronunciation(request.getPronunciation())
                        .exampleSentence(request.getExampleSentence())
                        .notes(request.getNote())
                        .submittedBy(user)
                        .status(SubmissionStatus.PENDING)
                        .build();

                TranslationSubmission saved = submissionRepository.save(submission);
                activityLogService.log(ActivityType.ADD_VOCABULARY,
                        "Submitted dictionary entry '" + sourceWord + "' (" + sourceLanguage.getName() + ") for review",
                        saved.getId(), null);

                savedSubmissions.add(saved);
            } catch (Exception e) {
                errors.add("Error processing word " + request.getSourceWord() + ": " + e.getMessage());
            }
        }

        if (savedSubmissions.isEmpty() && !errors.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("errors", errors);
            return ResponseEntity.status(409).body(response);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("saved", savedSubmissions);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<TranslationSubmission>> getMySubmissions() {
        User user = getCurrentUser();
        return ResponseEntity.ok(submissionRepository.findBySubmittedById(user.getId()));
    }

    // --- Admin / Reviewer Submissions review endpoints ---
    @GetMapping("/admin/submissions/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER', 'MODERATOR')")
    public ResponseEntity<List<TranslationSubmission>> getPendingSubmissions() {
        return ResponseEntity.ok(submissionRepository.findByStatus(SubmissionStatus.PENDING));
    }

    @GetMapping("/admin/submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER', 'MODERATOR')")
    public ResponseEntity<?> getSubmissionById(@PathVariable String id) {
        return submissionRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // The calling reviewer/moderator/admin's own past approve/reject actions.
    @GetMapping("/admin/submissions/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER', 'MODERATOR')")
    public ResponseEntity<List<TranslationSubmission>> getModerationHistory() {
        User reviewer = getCurrentUser();
        return ResponseEntity.ok(submissionRepository.findByReviewedByIdOrderByReviewedAtDesc(reviewer.getId()));
    }

    @PostMapping("/admin/submissions/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER', 'MODERATOR')")
    public ResponseEntity<?> approveSubmission(@PathVariable String id, @RequestBody(required = false) SubmissionReviewRequest request) {
        User reviewer = getCurrentUser();
        return submissionRepository.findById(id)
                .<ResponseEntity<?>>map(submission -> {
                    if (submission.getStatus() != SubmissionStatus.PENDING) {
                        return ResponseEntity.badRequest().body("Error: Submission is already resolved.");
                    }

                    Language sourceLanguage = submission.getSourceLanguage();
                    if (sourceLanguage == null || sourceLanguage.getId() == null) {
                        return ResponseEntity.badRequest().body(
                                "Error: The submission's source language no longer exists. Recreate that language or reject this submission.");
                    }

                    Optional<Language> bangla = languageRepository.findByCode(BANGLA_CODE);
                    Optional<Language> english = languageRepository.findByCode(ENGLISH_CODE);
                    if (bangla.isEmpty() || english.isEmpty()) {
                        return ResponseEntity.badRequest().body(
                                "Error: Both Bangla (code 'bn') and English (code 'en') languages must be configured before a submission can be approved.");
                    }

                    // The English translation is also the concept's canonical name. Normalizing it
                    // keeps "How are you" and "How are you " from becoming two separate concepts.
                    String conceptName = TextNormalizer.normalize(submission.getEnglishTranslation());
                    Concept concept = conceptRepository.findByNameIgnoreCase(conceptName)
                            .orElseGet(() -> conceptRepository.save(Concept.builder()
                                    .name(conceptName)
                                    .category(submission.getCategory())
                                    .build()));

                    // Always create/refresh three dictionary rows under this one concept:
                    // the source word, its Bangla meaning and its English meaning. This is what
                    // makes the word findable by a language-filtered search afterwards.
                    //
                    // The example sentence and reviewer notes describe the concept, not just the
                    // source word, so they are copied onto all three rows. The pronunciation is the
                    // phonetics of the source word specifically, so it stays only on the source row.
                    String notes = submission.getNotes();
                    String exampleSentence = submission.getExampleSentence();

                    List<String> saved = new ArrayList<>();
                    recordSaved(saved, upsertTranslation(concept, sourceLanguage, submission.getSourceWord(),
                            submission.getPronunciation(), notes, exampleSentence));

                    if (!sourceLanguage.getId().equals(bangla.get().getId())) {
                        recordSaved(saved, upsertTranslation(concept, bangla.get(), submission.getBanglaTranslation(), null, notes, exampleSentence));
                    }
                    if (!sourceLanguage.getId().equals(english.get().getId())) {
                        recordSaved(saved, upsertTranslation(concept, english.get(), submission.getEnglishTranslation(), null, notes, exampleSentence));
                    }

                    submission.setStatus(SubmissionStatus.APPROVED);
                    submission.setReviewedBy(reviewer);
                    submission.setReviewedAt(LocalDateTime.now());
                    if (request != null && request.getReviewerNote() != null) {
                        submission.setReviewerNote(request.getReviewerNote());
                    }
                    submissionRepository.save(submission);

                    User submitter = submission.getSubmittedBy();
                    activityLogService.log(ActivityType.ADD_VOCABULARY,
                            "Approved dictionary entry '" + submission.getSourceWord() + "'"
                                    + (submitter != null ? " from User: " + submitter.getEmail() : ""),
                            id, null);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("submission", submission);
                    body.put("conceptId", concept.getId());
                    body.put("translationsSaved", saved);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/submissions/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER', 'MODERATOR')")
    public ResponseEntity<?> rejectSubmission(@PathVariable String id, @RequestBody(required = false) SubmissionReviewRequest request) {
        if (request == null || request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
            return ResponseEntity.badRequest().body("Error: A rejection reason is required.");
        }

        User reviewer = getCurrentUser();
        return submissionRepository.findById(id)
                .map(submission -> {
                    if (submission.getStatus() != SubmissionStatus.PENDING) {
                        return ResponseEntity.badRequest().body("Error: Submission is already resolved.");
                    }

                    submission.setStatus(SubmissionStatus.REJECTED);
                    submission.setReviewedBy(reviewer);
                    submission.setReviewedAt(LocalDateTime.now());
                    submission.setRejectionReason(request.getRejectionReason());
                    if (request.getReviewerNote() != null) {
                        submission.setReviewerNote(request.getReviewerNote());
                    }

                    submissionRepository.save(submission);
                    activityLogService.log(ActivityType.ADD_VOCABULARY,
                            "Rejected dictionary entry '" + submission.getSourceWord() + "' from User: " + submission.getSubmittedBy().getEmail(),
                            id, null);

                    return ResponseEntity.ok(submission);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Creates the translation row for {@code concept} in {@code language}, or updates it if one
     * already exists. Returns {@code null} (creating nothing) when there is no language or no
     * usable text, so a blank Bangla/English field can never produce a language-less or empty row.
     */
    private Translation upsertTranslation(Concept concept, Language language, String text, String pronunciation, String notes, String exampleSentence) {
        if (language == null || language.getId() == null) {
            return null;
        }
        String normalizedText = TextNormalizer.normalize(text);
        if (normalizedText == null || normalizedText.isEmpty()) {
            return null;
        }

        Translation translation = translationRepository.findByConceptIdAndLanguageId(concept.getId(), language.getId())
                .orElseGet(Translation::new);

        translation.setConcept(concept);
        translation.setLanguage(language);
        translation.setText(normalizedText);
        if (pronunciation != null) translation.setPronunciation(TextNormalizer.normalize(pronunciation));
        if (notes != null) translation.setNotes(notes);
        if (exampleSentence != null) translation.setExampleSentence(exampleSentence);
        translation.setVerified(true);

        return translationRepository.save(translation);
    }

    private static void recordSaved(List<String> sink, Translation translation) {
        if (translation != null && translation.getLanguage() != null) {
            sink.add(translation.getLanguage().getName() + ": " + translation.getText());
        }
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
