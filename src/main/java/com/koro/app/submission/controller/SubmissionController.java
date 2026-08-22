package com.koro.app.submission.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.concept.entity.Concept;
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

    @Autowired
    private TranslationSubmissionRepository submissionRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private ConceptRepository conceptRepository;

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
        Concept concept = conceptRepository.findById(request.getConceptId())
                .orElseThrow(() -> new RuntimeException("Concept not found"));
        Language language = languageRepository.findById(request.getLanguageId())
                .orElseThrow(() -> new RuntimeException("Language not found"));

        TranslationSubmission submission = TranslationSubmission.builder()
                .concept(concept)
                .language(language)
                .suggestedTranslation(request.getSuggestedTranslation())
                .pronunciation(request.getPronunciation())
                .notes(request.getNotes())
                .submittedBy(user)
                .status(SubmissionStatus.PENDING)
                .build();

        TranslationSubmission saved = submissionRepository.save(submission);
        activityLogService.log(ActivityType.ADD_VOCABULARY, "Submitted translation suggestion for " + concept.getName() + " in " + language.getName(), saved.getId(), null);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<TranslationSubmission>> getMySubmissions() {
        User user = getCurrentUser();
        return ResponseEntity.ok(submissionRepository.findBySubmittedById(user.getId()));
    }

    // --- Admin / Reviewer Submissions review endpoints ---
    @GetMapping("/admin/submissions/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER')")
    public ResponseEntity<List<TranslationSubmission>> getPendingSubmissions() {
        return ResponseEntity.ok(submissionRepository.findByStatus(SubmissionStatus.PENDING));
    }

    @GetMapping("/admin/submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER')")
    public ResponseEntity<?> getSubmissionById(@PathVariable String id) {
        return submissionRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/submissions/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER')")
    public ResponseEntity<?> approveSubmission(@PathVariable String id, @RequestBody(required = false) SubmissionReviewRequest request) {
        User reviewer = getCurrentUser();
        return submissionRepository.findById(id)
                .map(submission -> {
                    if (submission.getStatus() != SubmissionStatus.PENDING) {
                        return ResponseEntity.badRequest().body("Error: Submission is already resolved.");
                    }

                    submission.setStatus(SubmissionStatus.APPROVED);
                    submission.setReviewedBy(reviewer);
                    submission.setReviewedAt(LocalDateTime.now());
                    if (request != null && request.getReviewerNote() != null) {
                        submission.setReviewerNote(request.getReviewerNote());
                    }

                    // Create or update the actual Translation record
                    Translation translation = translationRepository.findByConceptIdAndLanguageId(
                            submission.getConcept().getId(), 
                            submission.getLanguage().getId()
                    ).orElse(new Translation());

                    translation.setConcept(submission.getConcept());
                    translation.setLanguage(submission.getLanguage());
                    translation.setText(submission.getSuggestedTranslation());
                    translation.setPronunciation(submission.getPronunciation());
                    translation.setNotes(submission.getNotes());
                    translation.setVerified(true);

                    translationRepository.save(translation);
                    submissionRepository.save(submission);

                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Approved translation submission from User: " + submission.getSubmittedBy().getEmail(), id, null);

                    return ResponseEntity.ok(submission);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/submissions/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'LANGUAGE_REVIEWER')")
    public ResponseEntity<?> rejectSubmission(@PathVariable String id, @RequestBody(required = false) SubmissionReviewRequest request) {
        User reviewer = getCurrentUser();
        return submissionRepository.findById(id)
                .map(submission -> {
                    if (submission.getStatus() != SubmissionStatus.PENDING) {
                        return ResponseEntity.badRequest().body("Error: Submission is already resolved.");
                    }

                    submission.setStatus(SubmissionStatus.REJECTED);
                    submission.setReviewedBy(reviewer);
                    submission.setReviewedAt(LocalDateTime.now());
                    if (request != null && request.getReviewerNote() != null) {
                        submission.setReviewerNote(request.getReviewerNote());
                    }

                    submissionRepository.save(submission);
                    activityLogService.log(ActivityType.ADD_VOCABULARY, "Rejected translation submission from User: " + submission.getSubmittedBy().getEmail(), id, null);

                    return ResponseEntity.ok(submission);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
