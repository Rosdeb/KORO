package com.koro.app.admin.controller;

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

@RestController
@RequestMapping("/api/v1/admin/dictionary")
@RequiredArgsConstructor
public class DictionaryExportController {

    private final DictionaryExportService exportService;

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(name = "format", defaultValue = "csv", required = false) String format) {

        StreamingResponseBody stream = output -> {
            // UTF-8 BOM for Excel compatibility
            output.write(new byte[]{
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
            });

            exportService.writeAllCsv(output);
        };

        String filename = "korot-dictionary-" + LocalDate.now() + ".csv";

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
