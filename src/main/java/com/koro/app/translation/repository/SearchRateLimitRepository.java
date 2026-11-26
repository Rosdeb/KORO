package com.koro.app.translation.repository;

import com.koro.app.translation.entity.SearchRateLimit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchRateLimitRepository extends MongoRepository<SearchRateLimit, String> {
    Optional<SearchRateLimit> findByIdentifier(String identifier);
}
