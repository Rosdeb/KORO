package com.koro.app.submission.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
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
import java.util.List;

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
    public ResponseEntity<?> submitTranslation(@Valid @RequestBody SubmissionRequest request) {
        User user = getCurrentUser();

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));
        Language sourceLanguage = languageRepository.findById(request.getSourceLanguageId())
                .orElseThrow(() -> new RuntimeException("Source language not found"));

        String sourceWord = request.getSourceWord().trim();

        boolean existsInDictionary = !translationRepository
                .findByLanguageIdAndTextIgnoreCase(sourceLanguage.getId(), sourceWord).isEmpty();
        boolean alreadySubmitted = !submissionRepository
                .findBySourceLanguageIdAndSourceWordIgnoreCaseAndStatusNot(
                        sourceLanguage.getId(), sourceWord, SubmissionStatus.REJECTED)
                .isEmpty();

        if (existsInDictionary || alreadySubmitted) {
            return ResponseEntity.status(409)
                    .body("Error: This word already exists or has already been submitted for review.");
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

        return ResponseEntity.ok(saved);
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
                .map(submission -> {
                    if (submission.getStatus() != SubmissionStatus.PENDING) {
                        return ResponseEntity.badRequest().body("Error: Submission is already resolved.");
                    }

                    Language bangla = languageRepository.findByCode(BANGLA_CODE)
                            .orElseThrow(() -> new RuntimeException("Bangla language (code 'bn') is not configured"));
                    Language english = languageRepository.findByCode(ENGLISH_CODE)
                            .orElseThrow(() -> new RuntimeException("English language (code 'en') is not configured"));

                    Concept concept = conceptRepository.findByNameIgnoreCase(submission.getEnglishTranslation())
                            .orElseGet(() -> conceptRepository.save(Concept.builder()
                                    .name(submission.getEnglishTranslation())
                                    .category(submission.getCategory())
                                    .build()));

                    upsertTranslation(concept, submission.getSourceLanguage(), submission.getSourceWord(),
                            submission.getPronunciation(), submission.getNotes(), submission.getExampleSentence());

                    if (!submission.getSourceLanguage().getId().equals(bangla.getId())) {
                        upsertTranslation(concept, bangla, submission.getBanglaTranslation(), null, null, null);
                    }
                    if (!submission.getSourceLanguage().getId().equals(english.getId())) {
                        upsertTranslation(concept, english, submission.getEnglishTranslation(), null, null, null);
                    }

                    submission.setStatus(SubmissionStatus.APPROVED);
                    submission.setReviewedBy(reviewer);
                    submission.setReviewedAt(LocalDateTime.now());
                    if (request != null && request.getReviewerNote() != null) {
                        submission.setReviewerNote(request.getReviewerNote());
                    }
                    submissionRepository.save(submission);

                    activityLogService.log(ActivityType.ADD_VOCABULARY,
                            "Approved dictionary entry '" + submission.getSourceWord() + "' from User: " + submission.getSubmittedBy().getEmail(),
                            id, null);

                    return ResponseEntity.ok(submission);
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

    private void upsertTranslation(Concept concept, Language language, String text, String pronunciation, String notes, String exampleSentence) {
        Translation translation = translationRepository.findByConceptIdAndLanguageId(concept.getId(), language.getId())
                .orElse(new Translation());

        translation.setConcept(concept);
        translation.setLanguage(language);
        translation.setText(text);
        if (pronunciation != null) translation.setPronunciation(pronunciation);
        if (notes != null) translation.setNotes(notes);
        if (exampleSentence != null) translation.setExampleSentence(exampleSentence);
        translation.setVerified(true);

        translationRepository.save(translation);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
