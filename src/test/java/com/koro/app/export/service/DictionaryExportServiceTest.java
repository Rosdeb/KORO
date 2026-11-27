package com.koro.app.export.service;

import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryExportServiceTest {

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private TranslationRepository translationRepository;

    @Mock
    private LanguageRepository languageRepository;

    private DictionaryExportService service;

    @BeforeEach
    void setUp() {
        service = new DictionaryExportService(conceptRepository, translationRepository, languageRepository);
    }

    @Test
    void testWriteCsv_DefaultLanguagesAndAllCategories() throws IOException {
        Language koch = Language.builder().id("lang-koch").name("Koch").code("kdq").build();
        Language bangla = Language.builder().id("lang-bn").name("Bangla").code("bn").build();
        Language english = Language.builder().id("lang-en").name("English").code("en").build();

        when(languageRepository.findAll()).thenReturn(List.of(koch, bangla, english));

        Concept c1 = Concept.builder().id("101").name("you").build();
        Concept c2 = Concept.builder().id("102").name("how, are you").build();
        Concept c3 = Concept.builder().id("103").name("incomplete").build();

        when(conceptRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1, c2, c3)))
                .thenReturn(new PageImpl<>(List.of()));

        Translation t1Koch = Translation.builder().concept(c1).language(koch).text("nang").build();
        Translation t1Bn = Translation.builder().concept(c1).language(bangla).text("তুমি").build();
        Translation t1En = Translation.builder().concept(c1).language(english).text("you").build();

        Translation t2Koch = Translation.builder().concept(c2).language(koch).text("biting").build();
        Translation t2Bn = Translation.builder().concept(c2).language(bangla).text("কেমন").build();
        Translation t2En = Translation.builder().concept(c2).language(english).text("how").build();

        Translation t3Koch = Translation.builder().concept(c3).language(koch).text("tongto").build();

        when(translationRepository.findByConceptIdIn(anyList()))
                .thenReturn(List.of(t1Koch, t1Bn, t1En, t2Koch, t2Bn, t2En, t3Koch));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeCsv(out, null, null);

        String csv = out.toString(StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("ID,Koch,Bangla,English\r\n"));
        assertTrue(csv.contains("101,nang,তুমি,you\r\n"));
        assertTrue(csv.contains("102,biting,কেমন,how\r\n"));
        assertTrue(csv.contains("103,tongto,,incomplete\r\n"));
    }

    @Test
    void testWriteCsv_FilteredByCategoryAndCustomLanguages() throws IOException {
        Language koch = Language.builder().id("lang-koch").name("Koch").code("kdq").build();
        Language english = Language.builder().id("lang-en").name("English").code("en").build();
        Language chakma = Language.builder().id("lang-ccp").name("Chakma").code("ccp").build();

        when(languageRepository.findAll()).thenReturn(List.of(koch, english, chakma));

        Concept c1 = Concept.builder().id("201").name("Tree").build();

        when(conceptRepository.findByCategoryId(eq("cat-nature"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1)))
                .thenReturn(new PageImpl<>(List.of()));

        Translation t1Koch = Translation.builder().concept(c1).language(koch).text("Phang").build();
        Translation t1Ccp = Translation.builder().concept(c1).language(chakma).text("Gachh").build();

        when(translationRepository.findByConceptIdIn(anyList()))
                .thenReturn(List.of(t1Koch, t1Ccp));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeCsv(out, "cat-nature", List.of("lang-koch", "lang-ccp"));

        String csv = out.toString(StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("ID,Koch,Chakma\r\n"));
        assertTrue(csv.contains("201,Phang,Gachh\r\n"));
    }

    @Test
    void testEscapeCsv() {
        assertEquals("", DictionaryExportService.escapeCsv(null));
        assertEquals("hello", DictionaryExportService.escapeCsv("hello"));
        assertEquals("\"hello, world\"", DictionaryExportService.escapeCsv("hello, world"));
        assertEquals("\"hello \"\"quote\"\"\"", DictionaryExportService.escapeCsv("hello \"quote\""));
        assertEquals("\"line1\nline2\"", DictionaryExportService.escapeCsv("line1\nline2"));
    }
}
