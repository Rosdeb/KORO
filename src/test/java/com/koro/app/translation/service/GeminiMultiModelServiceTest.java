package com.koro.app.translation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiMultiModelServiceTest {

    private GeminiMultiModelService service;

    @BeforeEach
    void setUp() {
        service = new GeminiMultiModelService("gemini-3.8-flash,gemini-3.5-flash,gemini-flash-latest,gemini-pro-latest", new ObjectMapper());
    }

    @Test
    void loadsConfiguredModels() {
        List<String> models = service.getModels();
        assertEquals(4, models.size());
        assertEquals("gemini-3.8-flash", models.get(0));
        assertEquals("gemini-3.5-flash", models.get(1));
        assertEquals("gemini-flash-latest", models.get(2));
        assertEquals("gemini-pro-latest", models.get(3));
    }

    @Test
    void adminCanDynamicallyUpdateModelRoster() {
        service.setModels(List.of("gemini-2.5-flash", "gemini-2.0-flash", "custom-model"));
        List<String> updated = service.getModels();
        assertEquals(List.of("gemini-2.5-flash", "gemini-2.0-flash", "custom-model"), updated);
    }

    @Test
    void adminCanAddModel() {
        service.addModel("gemini-custom-12");
        assertTrue(service.getModels().contains("gemini-custom-12"));
    }

    @Test
    void defaultConstructorInstantiatesSuccessfully() {
        GeminiMultiModelService defaultService = new GeminiMultiModelService();
        assertNotNull(defaultService);
        assertFalse(defaultService.getModels().isEmpty());
    }

    @Test
    void whenDisabledReturnsEmpty() {
        service.setEnabled(false);
        var res = service.translateWithFallback("hello", null, null, null, null);
        assertTrue(res.isEmpty());
    }

    @Test
    void barrierBlocksAiWhenDatabaseVocabularyIsEmpty() {
        service.setEnabled(true);
        service.setApiKey("test-key");
        // No database vocabulary -> barrier must immediately return Optional.empty() without calling external API
        var res = service.translateWithFallback("randomword", null, null, null, null, List.of());
        assertTrue(res.isEmpty());
    }
}
