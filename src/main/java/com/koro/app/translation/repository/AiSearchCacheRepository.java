package com.koro.app.translation.repository;

import com.koro.app.translation.entity.AiSearchCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiSearchCacheRepository extends MongoRepository<AiSearchCache, String> {

    Optional<AiSearchCache> findByNormalizedQueryAndTargetLanguageId(String normalizedQuery, String targetLanguageId);

    Optional<AiSearchCache> findByNormalizedQuery(String normalizedQuery);

    List<AiSearchCache> findAllByOrderByHitCountDesc();

    Page<AiSearchCache> findAllByOrderByHitCountDesc(Pageable pageable);
}
