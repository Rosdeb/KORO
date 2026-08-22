package com.koro.app.submission.repository;

import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.entity.TranslationSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TranslationSubmissionRepository extends MongoRepository<TranslationSubmission, String> {
    List<TranslationSubmission> findByStatus(SubmissionStatus status);
    List<TranslationSubmission> findBySubmittedById(String userId);
}
