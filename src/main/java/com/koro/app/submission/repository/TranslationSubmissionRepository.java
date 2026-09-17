package com.koro.app.submission.repository;

import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.entity.TranslationSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TranslationSubmissionRepository extends MongoRepository<TranslationSubmission, String> {
    List<TranslationSubmission> findByStatus(SubmissionStatus status);
    Page<TranslationSubmission> findByStatus(SubmissionStatus status, Pageable pageable);
    Page<TranslationSubmission> findBySubmittedById(String userId, Pageable pageable);
    List<TranslationSubmission> findBySubmittedById(String userId);
    List<TranslationSubmission> findBySourceLanguageIdAndSourceWordIgnoreCaseAndStatusNot(
            String sourceLanguageId, String sourceWord, SubmissionStatus status);
    Page<TranslationSubmission> findByReviewedByIdOrderByReviewedAtDesc(String reviewerId, Pageable pageable);
    List<TranslationSubmission> findByReviewedByIdOrderByReviewedAtDesc(String reviewerId);
}
