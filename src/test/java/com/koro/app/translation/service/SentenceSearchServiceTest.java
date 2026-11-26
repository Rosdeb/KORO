package com.koro.app.translation.service;

import com.koro.app.concept.entity.Concept;
import com.koro.app.language.entity.Language;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentenceSearchServiceTest {

    @Mock
    private TranslationRepository translationRepository;

    private SentenceSearchService service;
    private Language koch;
    private Language bangla;
    private Language english;

    @BeforeEach
    void setUp() {
        service = new SentenceSearchService();

        koch = Language.builder().id("lang-koch").name("Koch").code("koch").build();
        bangla = Language.builder().id("lang-bn").name("Bangla").code("bn").build();
        english = Language.builder().id("lang-en").name("English").code("en").build();

        Concept c1 = Concept.builder().id("c1").name("you").build();
        Concept c2 = Concept.builder().id("c2").name("how").build();
        Concept c3 = Concept.builder().id("c3").name("are").build();
        Concept cGreeting = Concept.builder().id("c-greeting").name("how are you").build();

        Translation tKochSentence = Translation.builder()
                .id("t1")
                .concept(cGreeting)
                .language(koch)
                .text("nang biting tongto ke")
                .pronunciation("nang biting tongto ke")
                .exampleSentence("nang biting tongto ke? ang bhalo tongto.")
                .build();

        Translation tKochWord1 = Translation.builder()
                .id("t-word1")
                .concept(c1)
                .language(koch)
                .text("nang")
                .pronunciation("nang")
                .build();

        Translation tKochWord2 = Translation.builder()
                .id("t-word2")
                .concept(c2)
                .language(koch)
                .text("biting")
                .pronunciation("biting")
                .build();

        Translation tKochWord3 = Translation.builder()
                .id("t-word3")
                .concept(c3)
                .language(koch)
                .text("tongto")
                .pronunciation("tongto")
                .build();

        Translation tEnglishSentence = Translation.builder()
                .id("t-en-sentence")
                .concept(cGreeting)
                .language(english)
                .text("How are you?")
                .build();

        service.rebuildTokenIndex(List.of(tKochSentence, tKochWord1, tKochWord2, tKochWord3, tEnglishSentence));
    }

    @Test
    void normalizesPunctuationAndWhitespace() {
        String input = "  Nang, biting!! tongto? ke...  ";
        String normalized = service.normalize(input);
        assertEquals("nang biting tongto ke", normalized);
    }

    @Test
    void splitsIntoTokensProperly() {
        List<String> tokens = service.tokenize("nang biting tongto ke");
        assertEquals(List.of("nang", "biting", "tongto", "ke"), tokens);
    }

    @Test
    void findsExactSentenceMatchWithHighestRank() {
        List<Translation> results = service.searchSentences("nang biting tongto ke?", null, "lang-koch", translationRepository);
        assertFalse(results.isEmpty());
        assertEquals("t1", results.get(0).getId());
    }

    @Test
    void findsPartialSentenceMatchWhenOnlySomeWordsGiven() {
        // User searches 2 out of 4 words: "nang biting"
        List<Translation> results = service.searchSentences("nang biting", null, "lang-koch", translationRepository);
        assertFalse(results.isEmpty());
        // The sentence containing both words or the word itself should be returned
        assertTrue(results.stream().anyMatch(t -> "t1".equals(t.getId())));
    }

    @Test
    void crossLanguageMatchFindsTargetTranslation() {
        // User searches English "How are you", wants Koch translation
        when(translationRepository.findByConceptIdAndLanguageId("c-greeting", "lang-koch"))
                .thenReturn(Optional.of(Translation.builder().id("t1").text("nang biting tongto ke").build()));

        List<Translation> results = service.searchSentences("how are you", "lang-en", "lang-koch", translationRepository);
        assertFalse(results.isEmpty());
        assertEquals("t1", results.get(0).getId());
    }

    @Test
    void extractsDatabaseVocabularyContextForQueryTokens() {
        when(translationRepository.findByConceptIdAndLanguageId("c-greeting", "lang-koch"))
                .thenReturn(Optional.of(Translation.builder().id("t1").text("nang biting tongto ke").pronunciation("nang biting tongto ke").build()));

        List<String> vocabContext = service.extractDatabaseVocabularyContext("how are you", "lang-koch", translationRepository);
        assertFalse(vocabContext.isEmpty());
        assertTrue(vocabContext.get(0).contains("nang biting tongto ke"));
    }
}
