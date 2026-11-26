package com.koro.app.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koro.app.translation.dto.RateLimitConfigDto;
import com.koro.app.translation.service.GeminiMultiModelService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRateLimitAndAiTest {

    @Mock
    private TranslationRateLimitService translationRateLimitService;

    @Mock
    private GeminiMultiModelService geminiMultiModelService;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getSearchRateLimitsReturnsConfig() throws Exception {
        RateLimitConfigDto config = new RateLimitConfigDto(true, 50, 500, 15, 150);
        when(translationRateLimitService.getConfig()).thenReturn(config);

        mockMvc.perform(get("/api/v1/admin/rate-limits/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDailyLimit").value(50))
                .andExpect(jsonPath("$.userMonthlyLimit").value(500))
                .andExpect(jsonPath("$.anonDailyLimit").value(15))
                .andExpect(jsonPath("$.anonMonthlyLimit").value(150))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updateSearchRateLimitsUpdatesAndReturnsConfig() throws Exception {
        RateLimitConfigDto updated = new RateLimitConfigDto(true, 100, 1000, 30, 300);
        when(translationRateLimitService.updateConfig(any(RateLimitConfigDto.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/rate-limits/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userDailyLimit").value(100))
                .andExpect(jsonPath("$.userMonthlyLimit").value(1000));
    }

    @Test
    void getAiModelsReturnsRoster() throws Exception {
        when(geminiMultiModelService.getModels()).thenReturn(List.of("gemini-1.5-flash", "gemini-2.0-flash"));
        when(geminiMultiModelService.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/v1/admin/ai/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0]").value("gemini-1.5-flash"))
                .andExpect(jsonPath("$.models[1]").value("gemini-2.0-flash"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updateAiModelsUpdatesRoster() throws Exception {
        when(geminiMultiModelService.getModels()).thenReturn(List.of("gemini-2.5-flash", "gemini-1.5-flash"));
        when(geminiMultiModelService.isEnabled()).thenReturn(true);

        mockMvc.perform(put("/api/v1/admin/ai/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "models": ["gemini-2.5-flash", "gemini-1.5-flash"],
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI models updated successfully"));
    }
}
