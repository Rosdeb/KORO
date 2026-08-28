package com.koro.app.translation.repository;

import com.koro.app.translation.entity.Translation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationRepository extends MongoRepository<Translation, String> {
    Optional<Translation> findByConceptIdAndLanguageId(String conceptId, String languageId);
    List<Translation> findByLanguageId(String languageId);
    List<Translation> findByConceptId(String conceptId);
    List<Translation> findByTextContainingIgnoreCase(String text);
    List<Translation> findByLanguageIdAndTextContainingIgnoreCase(String languageId, String text);
    List<Translation> findByLanguageIdAndTextIgnoreCase(String languageId, String text);

    List<Translation> findByPronunciationContainingIgnoreCase(String pronunciation);
    List<Translation> findByLanguageIdAndPronunciationContainingIgnoreCase(String languageId, String pronunciation);

    List<Translation> findByConceptIdIn(Collection<String> conceptIds);
}
