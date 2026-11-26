package com.koro.app.translation.service;

import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class SentenceSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SentenceSearchService.class);
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}&&[^'-]]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    // languageId -> (token -> List<Translation>)
    private final Map<String, Map<String, List<Translation>>> languageTokenIndex = new ConcurrentHashMap<>();

    // Global (token -> List<Translation>)
    private final Map<String, List<Translation>> globalTokenIndex = new ConcurrentHashMap<>();

    // translationId -> Set of normalized tokens
    private final Map<String, Set<String>> translationTokens = new ConcurrentHashMap<>();

    // translationId -> normalized full text
    private final Map<String, String> translationNormalizedText = new ConcurrentHashMap<>();

    // translationId -> normalized example sentence
    private final Map<String, String> translationNormalizedExample = new ConcurrentHashMap<>();

    public void rebuildTokenIndex(List<Translation> allTranslations) {
        long start = System.currentTimeMillis();

        Map<String, Map<String, List<Translation>>> newLangIndex = new ConcurrentHashMap<>();
        Map<String, List<Translation>> newGlobalIndex = new ConcurrentHashMap<>();
        Map<String, Set<String>> newTokensMap = new ConcurrentHashMap<>();
        Map<String, String> newNormText = new ConcurrentHashMap<>();
        Map<String, String> newNormExample = new ConcurrentHashMap<>();

        for (Translation t : allTranslations) {
            if (t.getId() == null) continue;

            String langId = (t.getLanguage() != null && t.getLanguage().getId() != null)
                    ? t.getLanguage().getId() : null;

            Set<String> tokens = new HashSet<>();

            if (t.getText() != null) {
                String normText = normalize(t.getText());
                newNormText.put(t.getId(), normText);
                tokens.addAll(tokenize(normText));
            }

            if (t.getPronunciation() != null) {
                tokens.addAll(tokenize(normalize(t.getPronunciation())));
            }

            if (t.getExampleSentence() != null) {
                String normEx = normalize(t.getExampleSentence());
                newNormExample.put(t.getId(), normEx);
                tokens.addAll(tokenize(normEx));
            }

            newTokensMap.put(t.getId(), tokens);

            for (String token : tokens) {
                if (token.isEmpty()) continue;

                newGlobalIndex.computeIfAbsent(token, k -> new ArrayList<>()).add(t);

                if (langId != null) {
                    newLangIndex.computeIfAbsent(langId, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(token, k -> new ArrayList<>()).add(t);
                }
            }
        }

        languageTokenIndex.clear();
        languageTokenIndex.putAll(newLangIndex);

        globalTokenIndex.clear();
        globalTokenIndex.putAll(newGlobalIndex);

        translationTokens.clear();
        translationTokens.putAll(newTokensMap);

        translationNormalizedText.clear();
        translationNormalizedText.putAll(newNormText);

        translationNormalizedExample.clear();
        translationNormalizedExample.putAll(newNormExample);

        logger.info("Sentence & token index rebuilt for {} translations in {} ms",
                allTranslations.size(), (System.currentTimeMillis() - start));
    }

    public List<Translation> searchSentences(
            String rawQuery,
            String sourceLanguageId,
            String targetLanguageId,
            TranslationRepository translationRepository) {

        String normalizedQuery = normalize(rawQuery);
        List<String> queryTokens = tokenize(normalizedQuery);

        if (queryTokens.isEmpty()) {
            return List.of();
        }

        Map<String, ScoredCandidate> candidatesMap = new HashMap<>();

        if (sourceLanguageId != null && targetLanguageId != null) {
            // Cross-Language: Match tokens in source language index, then resolve target translations by concept
            Map<String, List<Translation>> sourceIndex = languageTokenIndex.getOrDefault(sourceLanguageId, Map.of());

            for (String token : queryTokens) {
                List<Translation> matchedInSource = sourceIndex.getOrDefault(token, List.of());
                for (Translation st : matchedInSource) {
                    if (st.getConcept() != null && st.getConcept().getId() != null) {
                        String conceptId = st.getConcept().getId();
                        // Get or compute candidate score
                        int score = scoreTranslation(st, normalizedQuery, queryTokens);
                        candidatesMap.merge(conceptId, new ScoredCandidate(conceptId, st, score),
                                (oldVal, newVal) -> oldVal.score >= newVal.score ? oldVal : newVal);
                    }
                }
            }

            // Retrieve target translations for the matched concepts
            List<Translation> targetResults = new ArrayList<>();
            List<ScoredCandidate> sortedCandidates = new ArrayList<>(candidatesMap.values());
            sortedCandidates.sort((a, b) -> Integer.compare(b.score, a.score));

            for (ScoredCandidate cand : sortedCandidates) {
                translationRepository.findByConceptIdAndLanguageId(cand.conceptId, targetLanguageId)
                        .ifPresent(targetResults::add);
            }

            return targetResults;

        } else if (targetLanguageId != null) {
            // Search target language tokens and also map from other languages' concepts
            Map<String, List<Translation>> langIndex = languageTokenIndex.getOrDefault(targetLanguageId, Map.of());

            for (String token : queryTokens) {
                // Direct target language match
                List<Translation> matches = langIndex.getOrDefault(token, List.of());
                for (Translation t : matches) {
                    int score = scoreTranslation(t, normalizedQuery, queryTokens);
                    candidatesMap.merge(t.getId(), new ScoredCandidate(t.getId(), t, score),
                            (oldVal, newVal) -> oldVal.score >= newVal.score ? oldVal : newVal);
                }

                // Global match mapped to target language concept
                List<Translation> globalMatches = globalTokenIndex.getOrDefault(token, List.of());
                for (Translation gt : globalMatches) {
                    if (gt.getConcept() != null && gt.getConcept().getId() != null) {
                        String conceptId = gt.getConcept().getId();
                        translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
                                .ifPresent(targetT -> {
                                    int score = scoreTranslation(gt, normalizedQuery, queryTokens);
                                    candidatesMap.merge(targetT.getId(), new ScoredCandidate(targetT.getId(), targetT, score),
                                            (oldVal, newVal) -> oldVal.score >= newVal.score ? oldVal : newVal);
                                });
                    }
                }
            }
        } else {
            // Global search
            for (String token : queryTokens) {
                List<Translation> matches = globalTokenIndex.getOrDefault(token, List.of());
                for (Translation t : matches) {
                    int score = scoreTranslation(t, normalizedQuery, queryTokens);
                    candidatesMap.merge(t.getId(), new ScoredCandidate(t.getId(), t, score),
                            (oldVal, newVal) -> oldVal.score >= newVal.score ? oldVal : newVal);
                }
            }
        }

        List<ScoredCandidate> ranked = new ArrayList<>(candidatesMap.values());
        ranked.sort((a, b) -> Integer.compare(b.score, a.score));

        List<Translation> results = new ArrayList<>(ranked.size());
        for (ScoredCandidate sc : ranked) {
            results.add(sc.translation);
        }

        return results;
    }

    public List<String> extractDatabaseVocabularyContext(
            String rawQuery,
            String targetLanguageId,
            TranslationRepository translationRepository) {

        String normalizedQuery = normalize(rawQuery);
        List<String> queryTokens = tokenize(normalizedQuery);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        Set<String> processedConceptIds = new HashSet<>();
        List<String> vocabList = new ArrayList<>();

        for (String token : queryTokens) {
            List<Translation> globalMatches = globalTokenIndex.getOrDefault(token, List.of());
            for (Translation gt : globalMatches) {
                if (gt.getConcept() != null && gt.getConcept().getId() != null) {
                    String conceptId = gt.getConcept().getId();
                    if (processedConceptIds.add(conceptId)) {
                        String conceptName = gt.getConcept().getName();
                        if (targetLanguageId != null) {
                            translationRepository.findByConceptIdAndLanguageId(conceptId, targetLanguageId)
                                    .ifPresent(targetT -> {
                                        vocabList.add(String.format("Concept: '%s' (Source word: '%s') -> Koch: '%s' (Pronunciation: '%s')",
                                                conceptName, gt.getText(), targetT.getText(),
                                                targetT.getPronunciation() != null ? targetT.getPronunciation() : targetT.getText()));
                                    });
                        } else {
                            vocabList.add(String.format("Concept: '%s' -> Word: '%s'", conceptName, gt.getText()));
                        }
                    }
                }
            }
        }
        return vocabList;
    }

    private int scoreTranslation(Translation t, String normQuery, List<String> queryTokens) {
        String candText = translationNormalizedText.getOrDefault(t.getId(), "");
        String candEx = translationNormalizedExample.getOrDefault(t.getId(), "");

        // 1. Exact sentence match = 100
        if (normQuery.equals(candText) || (!candEx.isEmpty() && normQuery.equals(candEx))) {
            return 100;
        }

        // 2. Exact phrase match = 90
        if (candText.contains(normQuery) || (!candEx.isEmpty() && candEx.contains(normQuery))) {
            return 90;
        }

        Set<String> candTokens = translationTokens.getOrDefault(t.getId(), Set.of());
        if (candTokens.isEmpty() || queryTokens.isEmpty()) {
            return 10;
        }

        int matchedCount = 0;
        for (String qToken : queryTokens) {
            if (candTokens.contains(qToken)) {
                matchedCount++;
            }
        }

        double coverage = (double) matchedCount / queryTokens.size();
        int baseScore;

        // 3. Token coverage scoring
        if (coverage >= 1.0) {
            baseScore = 80;
        } else if (coverage >= 0.75) {
            baseScore = 60;
        } else if (coverage >= 0.50) {
            baseScore = 40;
        } else if (coverage >= 0.25) {
            baseScore = 25;
        } else {
            baseScore = 15;
        }

        // 4. Consecutive word order bonus (+5)
        if (queryTokens.size() > 1 && candText.length() > 0) {
            for (int i = 0; i < queryTokens.size() - 1; i++) {
                String bigram = queryTokens.get(i) + " " + queryTokens.get(i + 1);
                if (candText.contains(bigram) || candEx.contains(bigram)) {
                    baseScore += 5;
                    break;
                }
            }
        }

        return baseScore;
    }

    public String normalize(String text) {
        if (text == null) return "";
        String normalized = TextNormalizer.normalize(text);
        if (normalized == null) return "";
        normalized = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    public List<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return List.of();
        }
        String[] parts = WHITESPACE_PATTERN.split(normalizedText);
        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isBlank()) {
                tokens.add(p);
            }
        }
        return tokens;
    }

    private static class ScoredCandidate {
        final String conceptId;
        final Translation translation;
        final int score;

        ScoredCandidate(String conceptId, Translation translation, int score) {
            this.conceptId = conceptId;
            this.translation = translation;
            this.score = score;
        }
    }
}
