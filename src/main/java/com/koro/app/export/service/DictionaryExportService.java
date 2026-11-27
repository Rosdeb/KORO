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
        writeCsv(output, null, null);
    }

    public void writeCsv(OutputStream output, String categoryId, List<String> requestedLanguageIds) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));

        // 1. Resolve languages
        List<Language> allLanguages = languageRepository.findAll();
        List<Language> selectedLanguages = new ArrayList<>();

        if (requestedLanguageIds != null && !requestedLanguageIds.isEmpty()) {
            List<String> targetIds = requestedLanguageIds.stream()
                    .flatMap(s -> Arrays.stream(s.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();

            for (String target : targetIds) {
                Language match = allLanguages.stream()
                        .filter(l -> l.getId().equalsIgnoreCase(target)
                                || (l.getCode() != null && l.getCode().equalsIgnoreCase(target))
                                || (l.getName() != null && l.getName().equalsIgnoreCase(target)))
                        .findFirst()
                        .orElse(null);
                if (match != null && !selectedLanguages.contains(match)) {
                    selectedLanguages.add(match);
                }
            }
        }

        // Default to Koch, Bangla, English if no specific language matched
        if (selectedLanguages.isEmpty()) {
            allLanguages.stream()
                    .filter(this::isKoch)
                    .findFirst()
                    .ifPresent(selectedLanguages::add);

            allLanguages.stream()
                    .filter(this::isBangla)
                    .findFirst()
                    .ifPresent(selectedLanguages::add);

            allLanguages.stream()
                    .filter(this::isEnglish)
                    .findFirst()
                    .ifPresent(selectedLanguages::add);
        }

        if (selectedLanguages.isEmpty()) {
            selectedLanguages.addAll(allLanguages);
        }

        // CSV Header: ID followed by each selected language name
        StringBuilder header = new StringBuilder("ID");
        for (Language lang : selectedLanguages) {
            header.append(",").append(escapeCsv(lang.getName()));
        }
        header.append("\r\n");
        writer.write(header.toString());

        boolean filterCategory = categoryId != null && !categoryId.isBlank() && !categoryId.equalsIgnoreCase("ALL");

        int page = 0;
        long totalExported = 0;
        Page<Concept> conceptPage;

        do {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            if (filterCategory) {
                conceptPage = conceptRepository.findByCategoryId(categoryId, pageable);
            } else {
                conceptPage = conceptRepository.findAll(pageable);
            }

            List<Concept> concepts = conceptPage.getContent();
            if (concepts.isEmpty()) {
                break;
            }

            List<String> conceptIds = concepts.stream()
                    .map(Concept::getId)
                    .filter(Objects::nonNull)
                    .toList();

            List<Translation> translations = translationRepository.findByConceptIdIn(conceptIds);

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

                StringBuilder row = new StringBuilder(escapeCsv(conceptId));

                for (Language lang : selectedLanguages) {
                    String text = "";
                    for (Translation t : conceptTranslations) {
                        if (t.getText() == null || t.getText().isBlank()) continue;
                        Language tLang = t.getLanguage();
                        if (tLang != null) {
                            if (tLang.getId().equals(lang.getId())
                                    || (lang.getCode() != null && lang.getCode().equalsIgnoreCase(tLang.getCode()))
                                    || (lang.getName() != null && lang.getName().equalsIgnoreCase(tLang.getName()))) {
                                text = t.getText().trim();
                                break;
                            }
                        }
                    }

                    // Fallback to concept canonical name for English if translation text is omitted
                    if (text.isEmpty() && isEnglish(lang)) {
                        if (concept.getName() != null && !concept.getName().isBlank()) {
                            text = concept.getName().trim();
                        }
                    }

                    row.append(",").append(escapeCsv(text));
                }

                row.append("\r\n");
                writer.write(row.toString());
                totalExported++;
            }

            // Flush after each batch to stream response incrementally to client
            writer.flush();
            page++;
        } while (conceptPage.hasNext());

        log.info("Dictionary CSV export completed: {} concepts exported (category: {}, languages: {}).",
                totalExported, categoryId != null ? categoryId : "ALL", selectedLanguages.size());
    }

    private boolean isKoch(Language lang) {
        if (lang == null) return false;
        String code = lang.getCode() != null ? lang.getCode().trim().toLowerCase() : "";
        String name = lang.getName() != null ? lang.getName().trim().toLowerCase() : "";
        return code.equals("koch") || code.equals("kdq") || code.equals("ko") || name.contains("koch");
    }

    private boolean isBangla(Language lang) {
        if (lang == null) return false;
        String code = lang.getCode() != null ? lang.getCode().trim().toLowerCase() : "";
        String name = lang.getName() != null ? lang.getName().trim().toLowerCase() : "";
        return code.equals("bn") || code.equals("ben") || name.contains("bangla") || name.contains("bengali");
    }

    private boolean isEnglish(Language lang) {
        if (lang == null) return false;
        String code = lang.getCode() != null ? lang.getCode().trim().toLowerCase() : "";
        String name = lang.getName() != null ? lang.getName().trim().toLowerCase() : "";
        return code.equals("en") || code.equals("eng") || name.contains("english");
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
