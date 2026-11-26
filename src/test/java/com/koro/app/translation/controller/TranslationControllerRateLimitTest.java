package com.koro.app.translation.controller;

import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.RateLimitResult;
import com.koro.app.translation.dto.TranslationSearchRequest;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.translation.service.GeminiMultiModelService;
import com.koro.app.translation.service.SentenceSearchService;
import com.koro.app.translation.service.TranslationRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TranslationControllerRateLimitTest {

    @Mock
    private TranslationRepository translationRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private TranslationRateLimitService translationRateLimitService;

    @Mock
    private SentenceSearchService sentenceSearchService;

    @Mock
    private GeminiMultiModelService geminiMultiModelService;

    @InjectMocks
    private TranslationController translationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(translationRepository.findAll()).thenReturn(List.of());
        translationController.initializeSearchIndex();
        mockMvc = MockMvcBuilders.standaloneSetup(translationController).build();
    }

    @Test
    void searchReturns429WhenRateLimitExceeded() throws Exception {
        when(translationRateLimitService.checkSearchQuota(any()))
                .thenReturn(RateLimitResult.denied("Daily search limit of 50 reached. Please try again tomorrow.", 50, 0, 500, 450));

        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "hello"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit-Daily", "50"))
                .andExpect(header().string("X-RateLimit-Remaining-Daily", "0"))
                .andExpect(header().string("X-RateLimit-Limit-Monthly", "500"))
                .andExpect(header().string("X-RateLimit-Remaining-Monthly", "450"))
                .andExpect(jsonPath("$.message").value("Daily search limit of 50 reached. Please try again tomorrow."));

        verifyNoInteractions(activityLogService);
        verify(translationRateLimitService, never()).consumeSearchQuota(any());
    }

    @Test
    void searchDoesNotConsumeQuotaWhenResultsEmpty() throws Exception {
        when(translationRateLimitService.checkSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 50, 500, 500));

        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "hello"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Daily", "50"))
                .andExpect(header().string("X-RateLimit-Remaining-Daily", "50"))
                .andExpect(content().json("[]"));

        verify(translationRateLimitService).checkSearchQuota(any());
        verify(translationRateLimitService, never()).consumeSearchQuota(any());
    }

    @Test
    void searchConsumesQuotaWhenResultsFound() throws Exception {
        when(translationRateLimitService.checkSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 50, 500, 500));
        when(translationRateLimitService.consumeSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 49, 500, 499));

        Concept concept = Concept.builder().id("c1").name("hello").build();
        Language lang = Language.builder().id("l1").name("Koch").build();
        Translation translation = Translation.builder()
                .id("t1")
                .concept(concept)
                .language(lang)
                .text("Nomoshkar")
                .build();

        when(sentenceSearchService.searchSentences(eq("hello"), any(), any(), any()))
                .thenReturn(List.of(translation));

        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "hello"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Daily", "50"))
                .andExpect(header().string("X-RateLimit-Remaining-Daily", "49"));

        verify(translationRateLimitService).checkSearchQuota(any());
        verify(translationRateLimitService).consumeSearchQuota(any());
        verify(activityLogService).log(any(), any(), any(), any());
    }

    @Test
    void searchReturnsBadRequestWhenQueryIsEmptyWithoutConsumingQuota() throws Exception {
        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Query string is empty"));

        verifyNoInteractions(translationRateLimitService);
    }

    @Test
    void crossLanguageSearchFindsNewlyAddedTranslationFromLiveDatabase() throws Exception {
        when(translationRateLimitService.checkSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 50, 500, 500));
        when(translationRateLimitService.consumeSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 49, 500, 499));

        Concept concept = Concept.builder().id("concept-tree").name("Tree").build();
        Language english = Language.builder().id("lang-en").name("English").build();
        Language koch = Language.builder().id("lang-koch").name("Koch").build();

        Translation englishTrans = Translation.builder()
                .id("t-en-1")
                .concept(concept)
                .language(english)
                .text("Tree")
                .build();

        Translation kochTrans = Translation.builder()
                .id("t-koch-1")
                .concept(concept)
                .language(koch)
                .text("Phang")
                .build();

        // Simulate new translation in DB not in RAM index:
        when(translationRepository.findByLanguageIdAndTextContainingIgnoreCase("lang-en", "Tree"))
                .thenReturn(List.of(englishTrans));
        when(translationRepository.findByConceptIdAndLanguageId("concept-tree", "lang-koch"))
                .thenReturn(Optional.of(kochTrans));

        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "Tree",
                                    "sourceLanguageId": "lang-en",
                                    "targetLanguageId": "lang-koch"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Phang"))
                .andExpect(jsonPath("$[0].conceptName").value("Tree"));

        verify(translationRateLimitService).consumeSearchQuota(any());
    }

    @Test
    void reindexSearchRebuildsIndexSuccessfully() throws Exception {
        when(translationRepository.findAll()).thenReturn(List.of());
        when(translationRepository.count()).thenReturn(150L);

        mockMvc.perform(post("/api/v1/admin/translations/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Search index rebuilt successfully from latest database state"))
                .andExpect(jsonPath("$.totalIndexed").value(150));
    }
}
