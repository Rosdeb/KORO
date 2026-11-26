package com.koro.app.translation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koro.app.concept.dto.ConceptDetails;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.dto.TranslationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class GeminiMultiModelService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiMultiModelService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<String> models = new CopyOnWriteArrayList<>();

    @Value("${app.gemini.api-key:}")
    private String apiKey;

    @Value("${app.gemini.enabled:true}")
    private boolean enabled;

    public GeminiMultiModelService() {
        this("gemini-3.8-flash,gemini-3.5-flash,gemini-flash-latest,gemini-pro-latest");
    }

    @Autowired
    public GeminiMultiModelService(
            @Value("${app.gemini.models:gemini-3.8-flash,gemini-3.5-flash,gemini-flash-latest,gemini-pro-latest}") String configuredModels) {
        this.restClient = createRestClient();
        this.objectMapper = new ObjectMapper();
        initModels(configuredModels);
    }

    public GeminiMultiModelService(String configuredModels, ObjectMapper objectMapper) {
        this.restClient = createRestClient();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        initModels(configuredModels);
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(factory).build();
    }

    private void initModels(String configuredModels) {
        models.clear();
        if (configuredModels != null && !configuredModels.isBlank()) {
            for (String m : configuredModels.split(",")) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty()) {
                    models.add(trimmed);
                }
            }
        }
        if (models.isEmpty()) {
            models.add("gemini-1.5-flash");
        }
    }

    public List<String> getModels() {
        return Collections.unmodifiableList(models);
    }

    public synchronized void setModels(List<String> newModels) {
        if (newModels != null && !newModels.isEmpty()) {
            models.clear();
            for (String m : newModels) {
                if (m != null && !m.isBlank()) {
                    models.add(m.trim());
                }
            }
        }
    }

    public synchronized void addModel(String model) {
        if (model != null && !model.isBlank() && !models.contains(model.trim())) {
            models.add(model.trim());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Translates a sentence or phrase using Google Gemini with multi-model fallback.
     * If the first model reaches its rate limit (429) or fails, it automatically falls back
     * to the next model in the configured roster.
     */
    public Optional<TranslationResponse> translateWithFallback(
            String query,
            String sourceLanguageId,
            String targetLanguageId,
            LanguageRepository languageRepository,
            ConceptRepository conceptRepository) {
        return translateWithFallback(query, sourceLanguageId, targetLanguageId, languageRepository, conceptRepository, List.of());
    }

    public Optional<TranslationResponse> translateWithFallback(
            String query,
            String sourceLanguageId,
            String targetLanguageId,
            LanguageRepository languageRepository,
            ConceptRepository conceptRepository,
            List<String> databaseVocab) {

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        // Strict Barrier: Ensure AI is NEVER called without database grounding vocabulary.
        // We do not allow searching the public web/browser or hallucinating non-existent words.
        if (databaseVocab == null || databaseVocab.isEmpty()) {
            logger.info("Barrier: No internal database vocabulary found for '{}'. Skipping AI to prevent external web hallucinations.", query);
            return Optional.empty();
        }

        String sourceLangName = resolveLanguageName(sourceLanguageId, languageRepository, "English");
        String targetLangName = resolveLanguageName(targetLanguageId, languageRepository, "Koch");

        Language targetLangEntity = (targetLanguageId != null)
                ? languageRepository.findById(targetLanguageId).orElse(null)
                : languageRepository.findByNameIgnoreCase("Koch").orElse(null);

        String prompt = buildPrompt(query, sourceLangName, targetLangName, databaseVocab);

        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            try {
                logger.info("Calling Gemini translation using model: {} (attempt {}/{})", model, (i + 1), models.size());
                TranslationResponse response = callGeminiApi(model, prompt, query, targetLangEntity);
                if (response != null) {
                    logger.info("Successfully translated sentence with model {}", model);
                    return Optional.of(response);
                }
            } catch (HttpClientErrorException.TooManyRequests e) {
                logger.warn("Model {} quota / rate limit reached (HTTP 429). Switching to next model in roster...", model);
            } catch (Exception e) {
                logger.warn("Model {} encountered error: {}. Trying next model...", model, e.getMessage());
            }
        }

        logger.error("All {} Gemini models exhausted or failed for query: {}", models.size(), query);
        return Optional.empty();
    }

    private TranslationResponse callGeminiApi(String model, String prompt, String originalQuery, Language targetLanguage) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );

        String responseJson = restClient.post()
                .uri(GEMINI_API_URL, model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        if (responseJson == null || responseJson.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }

        String contentText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        if (contentText == null || contentText.isBlank()) {
            return null;
        }

        String cleaned = contentText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        JsonNode parsed = objectMapper.readTree(cleaned);
        String text = parsed.path("text").asText("").trim();
        String pronunciation = parsed.path("pronunciation").asText("").trim();
        String notes = parsed.path("notes").asText("").trim();

        JsonNode exNode = parsed.path("exampleSentence");
        String exampleSentence = "";
        if (exNode.isObject()) {
            if (exNode.has("koch")) {
                exampleSentence = exNode.path("koch").asText();
                if (exNode.has("english")) {
                    exampleSentence += " (" + exNode.path("english").asText() + ")";
                }
            } else {
                exampleSentence = exNode.toString();
            }
        } else {
            exampleSentence = exNode.asText("").trim();
        }

        if (text.isEmpty()) {
            return null;
        }

        Concept syntheticConcept = Concept.builder()
                .id("ai-" + UUID.randomUUID().toString().substring(0, 8))
                .name(originalQuery)
                .description(notes)
                .build();

        return TranslationResponse.builder()
                .id("ai-gen-" + UUID.randomUUID().toString().substring(0, 8))
                .conceptId(syntheticConcept.getId())
                .conceptName(syntheticConcept.getName())
                .categoryName("AI Translation")
                .languageId(targetLanguage != null ? targetLanguage.getId() : "koch")
                .languageName(targetLanguage != null ? targetLanguage.getName() : "Koch")
                .text(text)
                .pronunciation(pronunciation)
                .notes(notes.isEmpty() ? "AI-assisted translation" : notes)
                .exampleSentence(exampleSentence.isEmpty() ? null : exampleSentence)
                .verified(false)
                .build();
    }

    private String buildPrompt(String query, String sourceLang, String targetLang, List<String> databaseVocab) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an authentic Koch language sentence constructor.\n");
        sb.append("STRICT RULE: Ground your translation strictly on the authentic vocabulary from our database provided below. Do NOT hallucinate or use unrelated public words.\n\n");

        if (databaseVocab != null && !databaseVocab.isEmpty()) {
            sb.append("AUTHENTIC DATABASE WORDS MATCHED FOR THIS QUERY:\n");
            for (String item : databaseVocab) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("DATABASE CONTEXT: None of the individual query tokens had exact direct matches in our database. Construct the sentence using standard Koch linguistic and grammatical rules.\n\n");
        }

        sb.append("GRAMMAR & SYNTAX RULES:\n");
        sb.append("- Follow Koch sentence structure (typically SOV: Subject - Object - Verb).\n");
        sb.append("- Apply question particles (e.g. 'ke' or 'se') at sentence end for questions.\n");
        sb.append("- Map English/Bangla grammatical roles (tense, pronouns, questions) to assemble the database words into a natural Koch sentence.\n\n");

        sb.append("Input Sentence to construct: \"").append(query).append("\"\n");
        sb.append("Source Language: ").append(sourceLang).append("\n");
        sb.append("Target Language: ").append(targetLang).append("\n\n");

        sb.append("Return ONLY a valid JSON object with keys: \"text\", \"pronunciation\", \"notes\", \"exampleSentence\".\n");
        sb.append("Example output format:\n");
        sb.append("{\n");
        sb.append("  \"text\": \"<Koch sentence assembled from database words>\",\n");
        sb.append("  \"pronunciation\": \"<phonetic Romanized pronunciation>\",\n");
        sb.append("  \"notes\": \"<clear breakdown of which database words were used>\",\n");
        sb.append("  \"exampleSentence\": \"<example usage>\"\n");
        sb.append("}");

        return sb.toString();
    }

    private String resolveLanguageName(String langId, LanguageRepository repo, String defaultName) {
        if (langId == null || langId.isBlank()) return defaultName;
        return repo.findById(langId).map(Language::getName).orElse(defaultName);
    }
}
