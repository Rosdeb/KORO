package com.koro.app.translation.service;

import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.TranslationResponse;
import com.koro.app.translation.entity.AiSearchCache;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.AiSearchCacheRepository;
import com.koro.app.translation.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiSearchCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AiSearchCacheService.class);

    private final AiSearchCacheRepository aiSearchCacheRepository;
    private final Map<String, TranslationResponse> l1Cache = new ConcurrentHashMap<>();

    public AiSearchCacheService(AiSearchCacheRepository aiSearchCacheRepository) {
        this.aiSearchCacheRepository = aiSearchCacheRepository;
    }

    private String getCacheKey(String normalizedQuery, String targetLanguageId) {
        return (targetLanguageId != null ? targetLanguageId : "all") + "::" + normalizedQuery;
    }

    public Optional<TranslationResponse> findInCache(String rawQuery, String targetLanguageId) {
        String normalizedQuery = normalize(rawQuery);
        if (normalizedQuery.isBlank()) {
            return Optional.empty();
        }

        String cacheKey = getCacheKey(normalizedQuery, targetLanguageId);
        TranslationResponse cachedMemory = l1Cache.get(cacheKey);
        if (cachedMemory != null) {
            // Asynchronously increment hit count without blocking the caller response
            CompletableFuture.runAsync(() -> {
                try {
                    Optional<AiSearchCache> cachedOpt = targetLanguageId != null
                            ? aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId(normalizedQuery, targetLanguageId)
                            : aiSearchCacheRepository.findByNormalizedQuery(normalizedQuery);
                    cachedOpt.ifPresent(cached -> {
                        cached.setHitCount(cached.getHitCount() + 1);
                        cached.setLastHitAt(LocalDateTime.now());
                        aiSearchCacheRepository.save(cached);
                    });
                } catch (Exception e) {
                    logger.debug("Failed to async-update hit count for cached query: {}", e.getMessage());
                }
            });
            return Optional.of(cachedMemory);
        }

        Optional<AiSearchCache> cachedOpt = targetLanguageId != null
                ? aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId(normalizedQuery, targetLanguageId)
                : aiSearchCacheRepository.findByNormalizedQuery(normalizedQuery);

        if (cachedOpt.isPresent()) {
            AiSearchCache cached = cachedOpt.get();
            try {
                cached.setHitCount(cached.getHitCount() + 1);
                cached.setLastHitAt(LocalDateTime.now());
                aiSearchCacheRepository.save(cached);
            } catch (Exception e) {
                logger.warn("Failed to update hit count for AI cache id {}: {}", cached.getId(), e.getMessage());
            }

            TranslationResponse response = toResponse(cached);
            l1Cache.put(cacheKey, response);
            return Optional.of(response);
        }

        return Optional.empty();
    }

    public void saveToCache(String rawQuery, String sourceLanguageId, String targetLanguageId,
                            TranslationResponse response, List<String> groundingVocab, String modelUsed) {
        if (response == null || response.getText() == null || response.getText().isBlank()) {
            return;
        }

        String normalizedQuery = normalize(rawQuery);
        if (normalizedQuery.isBlank()) {
            return;
        }

        // Put in memory L1 cache immediately for sub-millisecond retrieval
        String cacheKey = getCacheKey(normalizedQuery, targetLanguageId);
        l1Cache.put(cacheKey, response);

        try {
            Optional<AiSearchCache> existing = targetLanguageId != null
                    ? aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId(normalizedQuery, targetLanguageId)
                    : aiSearchCacheRepository.findByNormalizedQuery(normalizedQuery);

            if (existing.isPresent()) {
                AiSearchCache cache = existing.get();
                cache.setTargetText(response.getText());
                cache.setPronunciation(response.getPronunciation());
                cache.setNotes(response.getNotes());
                cache.setExampleSentence(response.getExampleSentence());
                cache.setGroundingVocab(groundingVocab);
                cache.setModelUsed(modelUsed);
                cache.setLastHitAt(LocalDateTime.now());
                cache.setHitCount(cache.getHitCount() + 1);
                aiSearchCacheRepository.save(cache);
            } else {
                AiSearchCache newCache = AiSearchCache.builder()
                        .normalizedQuery(normalizedQuery)
                        .rawQuery(rawQuery)
                        .sourceLanguageId(sourceLanguageId)
                        .targetLanguageId(targetLanguageId)
                        .targetLanguageName(response.getLanguageName())
                        .targetText(response.getText())
                        .pronunciation(response.getPronunciation())
                        .notes(response.getNotes())
                        .exampleSentence(response.getExampleSentence())
                        .groundingVocab(groundingVocab != null ? groundingVocab : List.of())
                        .modelUsed(modelUsed)
                        .hitCount(1)
                        .lastHitAt(LocalDateTime.now())
                        .verified(false)
                        .build();
                aiSearchCacheRepository.save(newCache);
                logger.info("Saved AI translation to ai_db_search cache for query: '{}'", rawQuery);
            }
        } catch (Exception e) {
            logger.warn("Failed to save to ai_db_search cache: {}", e.getMessage());
        }
    }

    public TranslationResponse toResponse(AiSearchCache cache) {
        return TranslationResponse.builder()
                .id("ai-cache-" + cache.getId())
                .conceptId("ai-c-" + cache.getId())
                .conceptName(cache.getRawQuery() != null ? cache.getRawQuery() : cache.getNormalizedQuery())
                .categoryName("AI Translation")
                .languageId(cache.getTargetLanguageId())
                .languageName(cache.getTargetLanguageName() != null ? cache.getTargetLanguageName() : "Koch")
                .text(cache.getTargetText())
                .pronunciation(cache.getPronunciation())
                .verified(cache.isVerified())
                .notes(cache.getNotes() != null ? cache.getNotes() : "Instant cached AI translation")
                .exampleSentence(cache.getExampleSentence())
                .build();
    }

    public Page<AiSearchCache> listCachedEntries(int page, int size) {
        return aiSearchCacheRepository.findAllByOrderByHitCountDesc(PageRequest.of(Math.max(0, page), Math.max(1, size)));
    }

    public boolean deleteCacheEntry(String id) {
        if (aiSearchCacheRepository.existsById(id)) {
            aiSearchCacheRepository.deleteById(id);
            l1Cache.clear();
            return true;
        }
        return false;
    }

    public Optional<Translation> promoteToDictionary(String cacheId, ConceptRepository conceptRepository,
                                                     TranslationRepository translationRepository,
                                                     LanguageRepository languageRepository) {
        return aiSearchCacheRepository.findById(cacheId).map(cache -> {
            String conceptName = cache.getRawQuery() != null && !cache.getRawQuery().isBlank()
                    ? cache.getRawQuery().trim()
                    : cache.getNormalizedQuery();

            Concept concept = conceptRepository.findByNameIgnoreCase(conceptName)
                    .orElseGet(() -> conceptRepository.save(Concept.builder()
                            .name(conceptName)
                            .description(cache.getNotes())
                            .build()));

            Language targetLang = (cache.getTargetLanguageId() != null)
                    ? languageRepository.findById(cache.getTargetLanguageId()).orElse(null)
                    : languageRepository.findByNameIgnoreCase("Koch").orElse(null);

            if (targetLang == null) {
                targetLang = languageRepository.findAll().stream().findFirst().orElse(null);
            }

            Translation translation = translationRepository.findByConceptIdAndLanguageId(concept.getId(), targetLang != null ? targetLang.getId() : null)
                    .orElseGet(Translation::new);

            translation.setConcept(concept);
            translation.setLanguage(targetLang);
            translation.setText(cache.getTargetText());
            translation.setPronunciation(cache.getPronunciation());
            translation.setNotes(cache.getNotes());
            translation.setExampleSentence(cache.getExampleSentence());
            translation.setVerified(true);

            Translation saved = translationRepository.save(translation);

            cache.setVerified(true);
            aiSearchCacheRepository.save(cache);
            l1Cache.clear();

            logger.info("Promoted AI cache entry '{}' to verified translation with ID: {}", cache.getId(), saved.getId());
            return saved;
        });
    }

    private String normalize(String text) {
        if (text == null) return "";
        return TextNormalizer.normalize(text).trim().toLowerCase();
    }
}
