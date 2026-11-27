package com.koro.app.admin.controller;

import com.koro.app.concept.entity.Category;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.export.service.DictionaryExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dictionary")
@RequiredArgsConstructor
public class DictionaryExportController {

    private final DictionaryExportService exportService;
    private final CategoryRepository categoryRepository;

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(name = "format", defaultValue = "csv", required = false) String format,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "languageIds", required = false) List<String> languageIds,
            @RequestParam(name = "languages", required = false) List<String> languages) {

        List<String> effectiveLanguageIds = (languageIds != null && !languageIds.isEmpty()) ? languageIds : languages;

        StreamingResponseBody stream = output -> {
            // UTF-8 BOM for Excel compatibility
            output.write(new byte[]{
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
            });

            exportService.writeCsv(output, categoryId, effectiveLanguageIds);
        };

        String filename = "korot-dictionary";
        if (categoryId != null && !categoryId.isBlank() && !categoryId.equalsIgnoreCase("ALL")) {
            String categoryName = categoryRepository.findById(categoryId)
                    .map(Category::getName)
                    .orElse(categoryId);
            String slug = categoryName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (!slug.isBlank()) {
                filename += "-" + slug;
            }
        }
        filename += "-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\""
            )
            .contentType(MediaType.parseMediaType(
                "text/csv; charset=UTF-8"
            ))
            .body(stream);
    }
}
