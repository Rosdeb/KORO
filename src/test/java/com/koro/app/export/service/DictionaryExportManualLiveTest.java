package com.koro.app.export.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Manual live benchmark test against real Atlas cluster")
public class DictionaryExportManualLiveTest {

    @Test
    void testRawMongoQuerySpeed() throws IOException {
        System.out.println("Connecting directly to MongoDB Atlas...");
        long start = System.currentTimeMillis();

        try (MongoClient client = MongoClients.create("mongodb+srv://rosdeb:Rosdeb%40321@cluster0.2evya.mongodb.net/KORO?retryWrites=true&w=majority")) {
            MongoTemplate template = new MongoTemplate(client, "KORO");

            long connected = System.currentTimeMillis();
            System.out.println("Connected in " + (connected - start) + " ms");

            // 1. Fetch languages
            long t0 = System.currentTimeMillis();
            List<Document> languages = template.getCollection("languages").find().into(new ArrayList<>());
            long t1 = System.currentTimeMillis();
            System.out.println("Loaded " + languages.size() + " languages in " + (t1 - t0) + " ms");

            // 2. Fetch translations (RAW BSON - NO N+1)
            t0 = System.currentTimeMillis();
            List<Document> translations = template.getCollection("translations").find().into(new ArrayList<>());
            t1 = System.currentTimeMillis();
            System.out.println("Loaded " + translations.size() + " translations in " + (t1 - t0) + " ms (SINGLE QUERY!)");

            // 3. Fetch concepts
            t0 = System.currentTimeMillis();
            List<Document> concepts = template.getCollection("concepts").find().sort(new Document("name", 1)).into(new ArrayList<>());
            t1 = System.currentTimeMillis();
            System.out.println("Loaded " + concepts.size() + " concepts in " + (t1 - t0) + " ms");

            // 4. Map in memory
            t0 = System.currentTimeMillis();
            Map<String, Map<String, String>> translationMap = new HashMap<>();
            for (Document doc : translations) {
                String cId = DictionaryExportService.extractId(doc.get("concept"));
                String lId = DictionaryExportService.extractId(doc.get("language"));
                String text = doc.getString("text");
                if (cId != null && lId != null && text != null && !text.isBlank()) {
                    translationMap.computeIfAbsent(cId, k -> new HashMap<>()).put(lId, text.trim());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            StringBuilder header = new StringBuilder("ID");
            List<Document> selectedLanguages = languages.stream().filter(l -> {
                String code = l.getString("code");
                String name = l.getString("name");
                return "kh".equalsIgnoreCase(code) || "bn".equalsIgnoreCase(code) || "en".equalsIgnoreCase(code)
                        || (name != null && (name.contains("Koch") || name.contains("Bangla") || name.contains("English")));
            }).toList();

            for (Document l : selectedLanguages) {
                header.append(",").append(DictionaryExportService.escapeCsv(l.getString("name")));
            }
            header.append("\r\n");
            out.write(header.toString().getBytes(StandardCharsets.UTF_8));

            for (Document concept : concepts) {
                String cid = DictionaryExportService.extractId(concept.get("_id"));
                StringBuilder row = new StringBuilder(DictionaryExportService.escapeCsv(cid));
                Map<String, String> byLang = translationMap.getOrDefault(cid, Collections.emptyMap());

                for (Document l : selectedLanguages) {
                    String lid = DictionaryExportService.extractId(l.get("_id"));
                    String text = byLang.getOrDefault(lid, "");
                    row.append(",").append(DictionaryExportService.escapeCsv(text));
                }
                row.append("\r\n");
                out.write(row.toString().getBytes(StandardCharsets.UTF_8));
            }

            long totalTime = System.currentTimeMillis() - start;
            System.out.println("TOTAL EXPORT TIME (Network + In-Memory Processing): " + totalTime + " ms");
            System.out.println("Generated CSV size: " + out.size() + " bytes");

            String sample = out.toString(StandardCharsets.UTF_8);
            System.out.println("--- CSV Preview (First 5 lines) ---");
            sample.lines().limit(5).forEach(System.out::println);
            System.out.println("-----------------------------------");

            assertTrue(totalTime < 5000, "Should complete under 5 seconds, actual: " + totalTime + "ms");
        }
    }
}
