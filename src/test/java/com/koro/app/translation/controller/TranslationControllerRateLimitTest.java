package com.koro.app.translation.controller;

import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.RateLimitResult;
import com.koro.app.translation.dto.TranslationSearchRequest;
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
        when(translationRateLimitService.checkAndConsumeSearchQuota(any()))
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
    }

    @Test
    void searchAllowsWhenWithinQuota() throws Exception {
        when(translationRateLimitService.checkAndConsumeSearchQuota(any()))
                .thenReturn(RateLimitResult.allowed(50, 49, 500, 499));

        mockMvc.perform(post("/api/v1/translations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "query": "hello"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit-Daily", "50"))
                .andExpect(header().string("X-RateLimit-Remaining-Daily", "49"))
                .andExpect(header().string("X-RateLimit-Limit-Monthly", "500"))
                .andExpect(header().string("X-RateLimit-Remaining-Monthly", "499"));

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
}
