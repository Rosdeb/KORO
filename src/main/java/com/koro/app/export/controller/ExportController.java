package com.koro.app.export.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.export.dto.PdfExportRequest;
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
        
        PdfExport pdfExport = pdfExportService.exportCollectionToPdf(
                user, 
                request.getCollectionId(), 
                request.getLanguageId()
        );

        // Log export activity
        activityLogService.log(
                ActivityType.EXPORT_PDF, 
                "Exported PDF book '" + pdfExport.getFileName() + "'", 
                pdfExport.getId(), 
                "collectionId=" + request.getCollectionId() + ", languageId=" + request.getLanguageId()
        );

        return ResponseEntity.ok(pdfExport);
    }

    @GetMapping("/history")
    public ResponseEntity<List<PdfExport>> getExportHistory() {
        User user = getCurrentUser();
        return ResponseEntity.ok(pdfExportRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getExportById(@PathVariable String id) {
        User user = getCurrentUser();
        return pdfExportRepository.findById(id)
                .map(pdfExport -> {
                    if (!pdfExport.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Error: Forbidden access");
                    }
                    return ResponseEntity.ok(pdfExport);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/files/{filename:.+}")
    public ResponseEntity<?> getExportFile(@PathVariable String filename) {
        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
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
