package com.koro.app.translation.service;

import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.TranslationResponse;
import com.koro.app.translation.entity.AiSearchCache;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.AiSearchCacheRepository;
import com.koro.app.translation.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSearchCacheServiceTest {

    @Mock
    private AiSearchCacheRepository aiSearchCacheRepository;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private TranslationRepository translationRepository;

    @Mock
    private LanguageRepository languageRepository;

    private AiSearchCacheService service;

    @BeforeEach
    void setUp() {
        service = new AiSearchCacheService(aiSearchCacheRepository);
    }

    @Test
    void findInCacheReturnsMappedResponseAndIncrementsHitCount() {
        AiSearchCache cache = AiSearchCache.builder()
                .id("cache-1")
                .normalizedQuery("tree and bamboo")
                .rawQuery("Tree and bamboo")
                .targetLanguageId("lang-koch")
                .targetLanguageName("Koch")
                .targetText("Phang ar waa")
                .pronunciation("phang ar waa")
                .hitCount(5)
                .build();

        when(aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId("tree and bamboo", "lang-koch"))
                .thenReturn(Optional.of(cache));
        when(aiSearchCacheRepository.save(any(AiSearchCache.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<TranslationResponse> respOpt = service.findInCache("Tree and bamboo", "lang-koch");

        assertTrue(respOpt.isPresent());
        TranslationResponse resp = respOpt.get();
        assertEquals("Phang ar waa", resp.getText());
        assertEquals("Koch", resp.getLanguageName());
        assertEquals(6, cache.getHitCount());
        verify(aiSearchCacheRepository).save(cache);
    }

    @Test
    void saveToCachePersistsNewEntry() {
        when(aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId("tree and bamboo", "lang-koch"))
                .thenReturn(Optional.empty());
        when(aiSearchCacheRepository.save(any(AiSearchCache.class))).thenAnswer(inv -> inv.getArgument(0));

        TranslationResponse tr = TranslationResponse.builder()
                .text("Phang ar waa")
                .pronunciation("phang ar waa")
                .languageName("Koch")
                .build();

        service.saveToCache("Tree and bamboo", "lang-en", "lang-koch", tr, List.of("Phang", "Waa"), "gemini-3.8-flash");

        verify(aiSearchCacheRepository).save(any(AiSearchCache.class));
    }

    @Test
    void promoteToDictionaryCreatesConceptAndTranslation() {
        AiSearchCache cache = AiSearchCache.builder()
                .id("cache-1")
                .rawQuery("Tree and bamboo")
                .normalizedQuery("tree and bamboo")
                .targetLanguageId("lang-koch")
                .targetText("Phang ar waa")
                .pronunciation("phang ar waa")
                .verified(false)
                .build();

        when(aiSearchCacheRepository.findById("cache-1")).thenReturn(Optional.of(cache));

        Concept concept = Concept.builder().id("c1").name("Tree and bamboo").build();
        when(conceptRepository.findByNameIgnoreCase("Tree and bamboo")).thenReturn(Optional.of(concept));

        Language lang = Language.builder().id("lang-koch").name("Koch").build();
        when(languageRepository.findById("lang-koch")).thenReturn(Optional.of(lang));

        Translation translation = Translation.builder().id("t1").concept(concept).language(lang).build();
        when(translationRepository.findByConceptIdAndLanguageId("c1", "lang-koch")).thenReturn(Optional.of(translation));
        when(translationRepository.save(any(Translation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(aiSearchCacheRepository.save(any(AiSearchCache.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Translation> promoted = service.promoteToDictionary("cache-1", conceptRepository, translationRepository, languageRepository);

        assertTrue(promoted.isPresent());
        assertEquals("Phang ar waa", promoted.get().getText());
        assertTrue(promoted.get().isVerified());
        assertTrue(cache.isVerified());
        verify(translationRepository).save(any(Translation.class));
        verify(aiSearchCacheRepository).save(cache);
    }

    @Test
    void l1CacheReturnsInstantHitWithoutQueryingDatabaseAgain() {
        AiSearchCache cache = AiSearchCache.builder()
                .id("cache-fast")
                .normalizedQuery("quick test")
                .rawQuery("Quick test")
                .targetLanguageId("lang-koch")
                .targetLanguageName("Koch")
                .targetText("Rani tang")
                .pronunciation("rani tang")
                .hitCount(1)
                .build();

        when(aiSearchCacheRepository.findByNormalizedQueryAndTargetLanguageId("quick test", "lang-koch"))
                .thenReturn(Optional.of(cache));
        when(aiSearchCacheRepository.save(any(AiSearchCache.class))).thenAnswer(inv -> inv.getArgument(0));

        // First call: populates L1 cache from repository
        Optional<TranslationResponse> first = service.findInCache("Quick test", "lang-koch");
        assertTrue(first.isPresent());
        assertEquals("Rani tang", first.get().getText());

        // Second call: must hit L1 cache immediately without querying repository again
        Optional<TranslationResponse> second = service.findInCache("Quick test", "lang-koch");
        assertTrue(second.isPresent());
        assertEquals("Rani tang", second.get().getText());

        // Verify repository query was only executed once!
        verify(aiSearchCacheRepository, times(1))
                .findByNormalizedQueryAndTargetLanguageId("quick test", "lang-koch");
    }
}
