package com.koro.app.export.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.koro.app.collection.CollectionOrdering;
import com.koro.app.collection.entity.Collection;
import com.koro.app.collection.entity.CollectionItem;
import com.koro.app.collection.repository.CollectionItemRepository;
import com.koro.app.collection.repository.CollectionRepository;
import com.koro.app.export.dto.PdfExportRequest;
import com.koro.app.export.entity.PdfExport;
import com.koro.app.export.repository.PdfExportRepository;
import com.koro.app.integration.storage.StorageService;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PdfExportService {

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private CollectionItemRepository itemRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private PdfExportRepository pdfExportRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private PdfFontProvider fonts;

    public PdfExport exportCollectionToPdf(User user, PdfExportRequest request) {
        Collection collection = collectionRepository.findById(request.getCollectionId())
                .orElseThrow(() -> new RuntimeException("Collection not found"));

        if (!collection.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Forbidden: You do not own this collection.");
        }

        List<String> languageIds = request.resolvedLanguageIds();
        if (languageIds.isEmpty()) {
            throw new IllegalArgumentException("At least one languageId (or languageIds) is required.");
        }
        List<Language> languages = new ArrayList<>();
        for (String id : languageIds) {
            languages.add(languageRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Language not found: " + id)));
        }

        Language headwordLanguage = languages.get(0);
        if (request.getHeadwordLanguageId() != null) {
            headwordLanguage = languages.stream()
                    .filter(l -> l.getId().equals(request.getHeadwordLanguageId()))
                    .findFirst()
                    .orElse(headwordLanguage);
        }
        boolean includeExamples = request.includeExampleSentencesOrDefault();

        List<CollectionItem> items = itemRepository.findByCollectionId(collection.getId());
        List<String> chapterOrder = CollectionOrdering.resolveChapterOrder(
                collection.getChapterOrder(),
                items.stream().map(it -> it.getChapter() == null ? "General" : it.getChapter()).collect(Collectors.toSet()));
        items.sort(CollectionOrdering.itemComparator(chapterOrder));

        Map<String, List<CollectionItem>> itemsByChapter = new LinkedHashMap<>();
        for (CollectionItem item : items) {
            String chapter = item.getChapter() == null ? "General" : item.getChapter();
            itemsByChapter.computeIfAbsent(chapter, k -> new ArrayList<>()).add(item);
        }

        byte[] pdfBytes = render(collection, user, languages, headwordLanguage, includeExamples, itemsByChapter);

        String fileName = safeFileName(collection.getName()) + ".pdf";
        MultipartFile multipartFile = new ByteArrayMultipartFile(pdfBytes, fileName);
        String savedFilename = storageService.store(multipartFile);
        String fileUrl = "/api/v1/export/files/" + savedFilename;

        PdfExport pdfExport = PdfExport.builder()
                .user(user)
                .collection(collection)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize((long) pdfBytes.length)
                .build();

        return pdfExportRepository.save(pdfExport);
    }

    private byte[] render(Collection collection, User user, List<Language> languages, Language headwordLanguage,
                          boolean includeExamples, Map<String, List<CollectionItem>> itemsByChapter) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Cover page
            Paragraph title = new Paragraph(collection.getName(), fonts.font(collection.getName(), 28, true));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(150);
            document.add(title);

            Paragraph subtitle = new Paragraph("A multilingual vocabulary compilation",
                    fonts.font("A multilingual vocabulary compilation", 16, false));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingBefore(10);
            document.add(subtitle);

            String languageList = languages.stream()
                    .map(l -> l.getName() + (l.getNativeName() != null ? " (" + l.getNativeName() + ")" : ""))
                    .collect(Collectors.joining(", "));
            String info = "\n\n\n\n\n\n\n\n\nAuthor: " + user.getName()
                    + "\nLanguages: " + languageList
                    + "\nGenerated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Paragraph authorInfo = new Paragraph(info, fonts.font(info, 12, false));
            authorInfo.setAlignment(Element.ALIGN_CENTER);
            authorInfo.setSpacingBefore(50);
            document.add(authorInfo);

            document.newPage();

            for (Map.Entry<String, List<CollectionItem>> entry : itemsByChapter.entrySet()) {
                String chapterName = entry.getKey();
                Paragraph chapterHeader = new Paragraph("Chapter — " + chapterName,
                        fonts.font(chapterName, 20, true));
                chapterHeader.setSpacingBefore(20);
                chapterHeader.setSpacingAfter(15);
                document.add(chapterHeader);

                int counter = 1;
                for (CollectionItem item : entry.getValue()) {
                    document.add(renderEntry(counter++, item, languages, headwordLanguage, includeExamples));
                }
                document.newPage();
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF book", e);
        }
    }

    private Paragraph renderEntry(int number, CollectionItem item, List<Language> languages,
                                  Language headwordLanguage, boolean includeExamples) {
        String conceptId = item.getConcept().getId();
        Map<String, Translation> byLanguage = new LinkedHashMap<>();
        for (Language language : languages) {
            translationRepository.findByConceptIdAndLanguageId(conceptId, language.getId())
                    .ifPresent(t -> byLanguage.put(language.getId(), t));
        }

        Translation headwordTranslation = byLanguage.get(headwordLanguage.getId());
        String headword = headwordTranslation != null && headwordTranslation.getText() != null
                ? headwordTranslation.getText()
                : item.getConcept().getName();

        Paragraph entry = new Paragraph();
        entry.setSpacingBefore(10);
        entry.setSpacingAfter(10);
        entry.add(new Chunk(number + ". " + headword + "\n", fonts.font(headword, 14, true)));

        for (Language language : languages) {
            Translation t = byLanguage.get(language.getId());
            String text = t != null && t.getText() != null ? t.getText() : "—";
            String pronunciation = t != null && t.getPronunciation() != null && !t.getPronunciation().isBlank()
                    ? " (" + t.getPronunciation() + ")" : "";
            String line = "   " + language.getName() + ": " + text + pronunciation + "\n";
            entry.add(new Chunk(line, fonts.font(text, 12, false)));

            if (includeExamples && t != null && t.getExampleSentence() != null && !t.getExampleSentence().isBlank()) {
                String exampleLine = "      e.g. " + t.getExampleSentence() + "\n";
                entry.add(new Chunk(exampleLine, fonts.font(t.getExampleSentence(), 10, false)));
            }
        }

        if (item.getNotes() != null && !item.getNotes().trim().isEmpty()) {
            String noteLine = "   Notes: " + item.getNotes() + "\n";
            entry.add(new Chunk(noteLine, fonts.font(item.getNotes(), 10, false)));
        }
        return entry;
    }

    private static String safeFileName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^a-zA-Z0-9.-]", "_");
        return cleaned.isBlank() ? "book" : cleaned;
    }

    // Inner helper class to wrap PDF byte array as MultipartFile
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String name;

        public ByteArrayMultipartFile(byte[] bytes, String name) {
            this.bytes = bytes;
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() { return name; }

        @Override
        public String getContentType() { return "application/pdf"; }

        @Override
        public boolean isEmpty() { return bytes.length == 0; }

        @Override
        public long getSize() { return bytes.length; }

        @Override
        public byte[] getBytes() throws IOException { return bytes; }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported in ByteArrayMultipartFile");
        }
    }
}
