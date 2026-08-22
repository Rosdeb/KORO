package com.koro.app.language.repository;

import com.koro.app.language.entity.Language;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LanguageRepository extends MongoRepository<Language, String> {
    Optional<Language> findByCode(String code);
    Optional<Language> findByNameIgnoreCase(String name);
    List<Language> findByActiveTrue();
}
