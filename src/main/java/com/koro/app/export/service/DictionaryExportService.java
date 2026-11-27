package com.koro.app.export.service;

import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    private static final int BATCH_SIZE = 500;

    public static class ConceptExportItem {
        private final String id;
        private final String name;

        public ConceptExportItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

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

        // 2. High-performance Direct MongoDB extraction:
        // By querying raw BSON documents via MongoTemplate, we completely eliminate the N+1 @DocumentReference
        // network queries (which caused 1600+ sequential roundtrips taking >5 minutes).
        boolean processedFast = false;
        if (mongoTemplate != null) {
            try {
                // Map: conceptId -> (languageKey -> text)
                Map<String, Map<String, String>> translationMap = new HashMap<>();
                for (org.bson.Document doc : mongoTemplate.getCollection("translations").find()) {
                    String cId = extractId(doc.get("concept"));
                    String lId = extractId(doc.get("language"));
                    String text = doc.getString("text");
                    if (cId != null && lId != null && text != null && !text.isBlank()) {
                        Map<String, String> langMap = translationMap.computeIfAbsent(cId, k -> new HashMap<>());
                        langMap.put(lId, text.trim());
                    }
                }

                List<ConceptExportItem> concepts = new ArrayList<>();
                org.bson.Document sortDoc = new org.bson.Document("name", 1);
                for (org.bson.Document doc : mongoTemplate.getCollection("concepts").find().sort(sortDoc)) {
                    String id = extractId(doc.get("_id"));
                    String name = doc.getString("name");
                    String catId = extractId(doc.get("category"));

                    if (filterCategory) {
                        if (catId != null && (catId.equalsIgnoreCase(categoryId) || catId.equals(categoryId))) {
                            concepts.add(new ConceptExportItem(id, name));
                        }
                    } else {
                        concepts.add(new ConceptExportItem(id, name));
                    }
                }

                if (!concepts.isEmpty()) {
                    long totalExported = 0;
                    for (ConceptExportItem concept : concepts) {
                        StringBuilder row = new StringBuilder(escapeCsv(concept.getId()));
                        Map<String, String> byLang = translationMap.getOrDefault(concept.getId(), Collections.emptyMap());

                        for (Language lang : selectedLanguages) {
                            String text = byLang.getOrDefault(lang.getId(), "");
                            if (text.isEmpty() && lang.getCode() != null) {
                                text = byLang.getOrDefault(lang.getCode(), "");
                            }
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
                    writer.flush();
                    processedFast = true;
                    log.info("Dictionary CSV fast export completed: {} concepts exported (category: {}, languages: {}).",
                            totalExported, categoryId != null ? categoryId : "ALL", selectedLanguages.size());
                }
            } catch (Exception e) {
                log.warn("MongoTemplate fast export encountered error, falling back to standard pagination: {}", e.getMessage());
            }
        }

        // 3. Fallback for unit tests and environments where MongoTemplate is not available or empty
        if (!processedFast) {
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

                List<Translation> translations = Collections.emptyList();
                try {
                    translations = translationRepository.findByConceptIdIn(conceptIds);
                } catch (Exception e) {
                    log.warn("findByConceptIdIn failed: {}", e.getMessage());
                }

                Map<String, List<Translation>> translationsByConceptId = new HashMap<>();
                if (translations != null) {
                    for (Translation t : translations) {
                        if (t.getConcept() != null && t.getConcept().getId() != null) {
                            translationsByConceptId
                                    .computeIfAbsent(t.getConcept().getId(), k -> new ArrayList<>())
                                    .add(t);
                        }
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

                writer.flush();
                page++;
            } while (conceptPage.hasNext());

            log.info("Dictionary CSV export completed: {} concepts exported (category: {}, languages: {}).",
                    totalExported, categoryId != null ? categoryId : "ALL", selectedLanguages.size());
        }
    }

    public static String extractId(Object ref) {
        if (ref == null) return null;
        if (ref instanceof com.mongodb.DBRef dbRef) {
            return dbRef.getId() != null ? dbRef.getId().toString() : null;
        }
        if (ref instanceof org.bson.Document doc) {
            Object id = doc.get("_id");
            if (id != null) return id.toString();
            id = doc.get("$id");
            if (id != null) return id.toString();
        }
        if (ref instanceof org.bson.types.ObjectId oid) {
            return oid.toHexString();
        }
        return ref.toString();
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
