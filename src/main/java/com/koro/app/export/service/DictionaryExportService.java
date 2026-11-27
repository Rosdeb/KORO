package com.koro.app.export.service;

import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DictionaryExportService {

    private final ConceptRepository conceptRepository;
    private final TranslationRepository translationRepository;
    private final LanguageRepository languageRepository;

    private static final int BATCH_SIZE = 500;

    public void writeAllCsv(OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));

        // 1. Identify language IDs for Koch, Bangla, English
        List<Language> languages = languageRepository.findAll();
        Set<String> kochLangIds = new HashSet<>();
        Set<String> banglaLangIds = new HashSet<>();
        Set<String> englishLangIds = new HashSet<>();

        for (Language lang : languages) {
            String code = lang.getCode() != null ? lang.getCode().trim().toLowerCase() : "";
            String name = lang.getName() != null ? lang.getName().trim().toLowerCase() : "";

            if (code.equals("koch") || code.equals("kdq") || code.equals("ko") || name.contains("koch")) {
                kochLangIds.add(lang.getId());
            } else if (code.equals("bn") || code.equals("ben") || name.contains("bangla") || name.contains("bengali")) {
                banglaLangIds.add(lang.getId());
            } else if (code.equals("en") || code.equals("eng") || name.contains("english")) {
                englishLangIds.add(lang.getId());
            }
        }

        // CSV Header
        writer.write("ID,Koch,Bangla,English\r\n");

        int page = 0;
        long totalExported = 0;
        Page<Concept> conceptPage;

        do {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            conceptPage = conceptRepository.findAll(pageable);
            List<Concept> concepts = conceptPage.getContent();

            if (concepts.isEmpty()) {
                break;
            }

            List<String> conceptIds = concepts.stream()
                    .map(Concept::getId)
                    .filter(Objects::nonNull)
                    .toList();

            // Fetch translations in bulk for this batch
            List<Translation> translations = translationRepository.findByConceptIdIn(conceptIds);

            // Group translations by concept ID
            Map<String, List<Translation>> translationsByConceptId = new HashMap<>();
            for (Translation t : translations) {
                if (t.getConcept() != null && t.getConcept().getId() != null) {
                    translationsByConceptId
                            .computeIfAbsent(t.getConcept().getId(), k -> new ArrayList<>())
                            .add(t);
                }
            }

            for (Concept concept : concepts) {
                String conceptId = concept.getId();
                List<Translation> conceptTranslations = translationsByConceptId.getOrDefault(conceptId, Collections.emptyList());

                String koch = "";
                String bangla = "";
                String english = "";

                for (Translation t : conceptTranslations) {
                    if (t.getText() == null || t.getText().isBlank()) {
                        continue;
                    }
                    String text = t.getText().trim();
                    Language lang = t.getLanguage();
                    String langId = lang != null ? lang.getId() : null;
                    String langCode = lang != null && lang.getCode() != null ? lang.getCode().trim().toLowerCase() : "";
                    String langName = lang != null && lang.getName() != null ? lang.getName().trim().toLowerCase() : "";

                    if ((langId != null && kochLangIds.contains(langId))
                            || langCode.equals("koch") || langCode.equals("kdq") || langName.contains("koch")) {
                        if (koch.isEmpty()) koch = text;
                    } else if ((langId != null && banglaLangIds.contains(langId))
                            || langCode.equals("bn") || langCode.equals("ben") || langName.contains("bangla") || langName.contains("bengali")) {
                        if (bangla.isEmpty()) bangla = text;
                    } else if ((langId != null && englishLangIds.contains(langId))
                            || langCode.equals("en") || langCode.equals("eng") || langName.contains("english")) {
                        if (english.isEmpty()) english = text;
                    }
                }

                // If English translation was omitted, fallback to canonical concept name
                if (english.isEmpty() && concept.getName() != null && !concept.getName().isBlank()) {
                    english = concept.getName().trim();
                }

                writer.write(escapeCsv(conceptId));
                writer.write(",");
                writer.write(escapeCsv(koch));
                writer.write(",");
                writer.write(escapeCsv(bangla));
                writer.write(",");
                writer.write(escapeCsv(english));
                writer.write("\r\n");

                totalExported++;
            }

            // Flush after each batch to stream response incrementally to client
            writer.flush();
            page++;
        } while (conceptPage.hasNext());

        log.info("Dictionary CSV export completed: {} concepts exported.", totalExported);
    }

    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
