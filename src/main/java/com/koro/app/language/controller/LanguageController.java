package com.koro.app.language.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.repository.TranslationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LanguageController {

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private TranslationRepository translationRepository;

    // Public / User access
    @GetMapping("/languages")
    public ResponseEntity<List<Language>> getAllLanguages() {
        return ResponseEntity.ok(languageRepository.findByActiveTrue());
    }

    @GetMapping("/languages/{id}")
    public ResponseEntity<?> getLanguageById(@PathVariable String id) {
        return languageRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Admin access
    @GetMapping("/admin/languages")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ResponseEntity<List<Language>> getAllLanguagesForAdmin() {
        return ResponseEntity.ok(languageRepository.findAll());
    }

    @PostMapping("/admin/languages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createLanguage(@RequestBody Language language) {
        if (languageRepository.findByCode(language.getCode()).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: ISO code already exists"));
        }
        Language saved = languageRepository.save(language);
        activityLogService.log(ActivityType.CHANGE_LANGUAGE, "Created language: " + saved.getName(), saved.getId(), null);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/admin/languages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> updateLanguage(@PathVariable String id, @RequestBody Language request) {
        return languageRepository.findById(id)
                .map(language -> {
                    if (request.getName() != null) language.setName(request.getName());
                    if (request.getNativeName() != null) language.setNativeName(request.getNativeName());
                    if (request.getCode() != null) language.setCode(request.getCode());
                    if (request.getRegion() != null) language.setRegion(request.getRegion());
                    language.setActive(request.isActive());
                    if (request.getDescription() != null) language.setDescription(request.getDescription());
                    
                    Language saved = languageRepository.save(language);
                    activityLogService.log(ActivityType.CHANGE_LANGUAGE, "Updated language: " + saved.getName(), saved.getId(), null);
                    return ResponseEntity.ok(saved);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/languages/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> deleteLanguage(@PathVariable String id) {
        return languageRepository.findById(id)
                .<ResponseEntity<?>>map(language -> {
                    if (!translationRepository.findByLanguageId(id).isEmpty()) {
                        return ResponseEntity.badRequest().body(new MessageResponse(
                                "Error: Language has existing translations and cannot be deleted"));
                    }
                    languageRepository.delete(language);
                    activityLogService.log(ActivityType.CHANGE_LANGUAGE, "Deleted language: " + language.getName(), id, null);
                    return ResponseEntity.ok(new MessageResponse("Language deleted successfully!"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
