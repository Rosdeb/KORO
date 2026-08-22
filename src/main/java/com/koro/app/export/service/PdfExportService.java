package com.koro.app.export.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.koro.app.collection.entity.Collection;
import com.koro.app.collection.entity.CollectionItem;
import com.koro.app.collection.repository.CollectionItemRepository;
import com.koro.app.collection.repository.CollectionRepository;
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

    public PdfExport exportCollectionToPdf(User user, String collectionId, String languageId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new RuntimeException("Collection not found"));
        
        if (!collection.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Forbidden: You do not own this collection.");
        }

        Language language = languageRepository.findById(languageId)
                .orElseThrow(() -> new RuntimeException("Language not found"));

        List<CollectionItem> items = itemRepository.findByCollectionId(collectionId);

        // Group items by chapter
        Map<String, List<CollectionItem>> itemsByChapter = items.stream()
                .collect(Collectors.groupingBy(CollectionItem::getChapter));

        byte[] pdfBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            
            document.open();

            // 1. Cover Page
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, Font.BOLD);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 16, Font.ITALIC);
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL);

            Paragraph title = new Paragraph(collection.getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(150);
            document.add(title);

            Paragraph subtitle = new Paragraph("A multilingual vocabulary compilation", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingBefore(10);
            document.add(subtitle);

            Paragraph authorInfo = new Paragraph(
                    "\n\n\n\n\n\n\n\n\nAuthor: " + user.getName() +
                    "\nTarget Language: " + language.getName() + " (" + language.getNativeName() + ")" +
                    "\nGenerated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    infoFont
            );
            authorInfo.setAlignment(Element.ALIGN_CENTER);
            authorInfo.setSpacingBefore(50);
            document.add(authorInfo);

            document.newPage();

            // 2. Chapters and Vocabulary Content
            Font chapterFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD);
            Font vocabFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD);
            Font translationFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL);
            Font notesFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC);

            for (Map.Entry<String, List<CollectionItem>> entry : itemsByChapter.entrySet()) {
                String chapterName = entry.getKey();
                List<CollectionItem> chapterItems = entry.getValue();

                Paragraph chapterHeader = new Paragraph("Chapter — " + chapterName, chapterFont);
                chapterHeader.setSpacingBefore(20);
                chapterHeader.setSpacingAfter(15);
                document.add(chapterHeader);

                int counter = 1;
                for (CollectionItem item : chapterItems) {
                    Translation translation = translationRepository.findByConceptIdAndLanguageId(
                            item.getConcept().getId(), 
                            language.getId()
                    ).orElse(null);

                    String text = (translation != null) ? translation.getText() : "[No Translation]";
                    String pronunciation = (translation != null && translation.getPronunciation() != null) 
                            ? " (" + translation.getPronunciation() + ")" : "";

                    Paragraph entryParagraph = new Paragraph();
                    entryParagraph.setSpacingBefore(10);
                    entryParagraph.setSpacingAfter(10);
                    
                    entryParagraph.add(new Chunk(counter + ". " + item.getConcept().getName() + "\n", vocabFont));
                    entryParagraph.add(new Chunk("   Translation: " + text + pronunciation + "\n", translationFont));
                    
                    if (item.getNotes() != null && !item.getNotes().trim().isEmpty()) {
                        entryParagraph.add(new Chunk("   Notes: " + item.getNotes() + "\n", notesFont));
                    }
                    
                    document.add(entryParagraph);
                    counter++;
                }
                document.newPage();
            }

            document.close();
            pdfBytes = baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF book", e);
        }

        // Store PDF in Storage
        String fileName = collection.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".pdf";
        MultipartFile multipartFile = new ByteArrayMultipartFile(pdfBytes, fileName);
        String savedFilename = storageService.store(multipartFile);
        String fileUrl = "/api/v1/export/files/" + savedFilename;

        // Save PDF Export Record
        PdfExport pdfExport = PdfExport.builder()
                .user(user)
                .collection(collection)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .fileSize((long) pdfBytes.length)
                .build();

        return pdfExportRepository.save(pdfExport);
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
