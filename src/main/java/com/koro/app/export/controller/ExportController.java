package com.koro.app.export.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.export.dto.PdfExportRequest;
import com.koro.app.export.dto.PdfExportResponse;
import com.koro.app.export.entity.PdfExport;
import com.koro.app.export.repository.PdfExportRepository;
import com.koro.app.export.service.PdfExportService;
import com.koro.app.integration.storage.StorageService;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    @Autowired
    private PdfExportService pdfExportService;

    @Autowired
    private PdfExportRepository pdfExportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ActivityLogService activityLogService;

    @PostMapping("/pdf")
    public ResponseEntity<?> exportToPdf(@Valid @RequestBody PdfExportRequest request) {
        User user = getCurrentUser();

        if (request.resolvedLanguageIds().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: at least one languageId (or languageIds) is required.");
        }

        PdfExport pdfExport = pdfExportService.exportCollectionToPdf(user, request);

        activityLogService.log(
                ActivityType.EXPORT_PDF,
                "Exported PDF book '" + pdfExport.getFileName() + "'",
                pdfExport.getId(),
                "collectionId=" + request.getCollectionId() + ", languageIds=" + request.resolvedLanguageIds()
        );

        return ResponseEntity.ok(PdfExportResponse.from(pdfExport));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PdfExportResponse>> getExportHistory() {
        User user = getCurrentUser();
        List<PdfExportResponse> history = pdfExportRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(PdfExportResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getExportById(@PathVariable String id) {
        User user = getCurrentUser();
        return pdfExportRepository.findById(id)
                .<ResponseEntity<?>>map(pdfExport -> {
                    if (!pdfExport.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Error: Forbidden access");
                    }
                    return ResponseEntity.ok(PdfExportResponse.from(pdfExport));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Public (see SecurityConfig): the filename is an unguessable UUID, so this doubles as the
    // shareable "published book" link. `inline` so it previews in the browser; add ?download=1
    // to force a save.
    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<?> getExportFile(@PathVariable String filename,
                                           @RequestParam(required = false) boolean download) {
        Resource file = storageService.loadAsResource(filename);
        String disposition = (download ? "attachment" : "inline") + "; filename=\"" + file.getFilename() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(file);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
