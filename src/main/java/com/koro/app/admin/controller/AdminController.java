package com.koro.app.admin.controller;

import com.koro.app.activity.repository.ActivityLogRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.translation.dto.RateLimitConfigDto;
import com.koro.app.translation.dto.UserRateLimitDto;
import com.koro.app.translation.dto.UserRateLimitUpdateRequest;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.translation.entity.AiSearchCache;
import com.koro.app.translation.service.AiSearchCacheService;
import com.koro.app.translation.service.GeminiMultiModelService;
import com.koro.app.translation.service.TranslationRateLimitService;
import com.koro.app.user.dto.UserResponse;
import com.koro.app.user.entity.Role;
import com.koro.app.user.entity.User;
import com.koro.app.user.entity.UserStatus;
import com.koro.app.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private TranslationSubmissionRepository submissionRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private TranslationRateLimitService translationRateLimitService;

    @Autowired
    private GeminiMultiModelService geminiMultiModelService;

    @Autowired
    private AiSearchCacheService aiSearchCacheService;

    @GetMapping("/statistics")
    public ResponseEntity<?> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalUsers", userRepository.count());
        stats.put("totalLanguages", languageRepository.count());
        stats.put("totalConcepts", conceptRepository.count());
        stats.put("totalTranslations", translationRepository.count());
        stats.put("pendingApprovals", submissionRepository.findByStatus(SubmissionStatus.PENDING).size());
        
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        stats.put("todayActivities", activityLogRepository.countByCreatedAtAfter(startOfToday));

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserResponse> responses = users.stream()
                .map(UserResponse::fromUser)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable String id, @RequestParam UserStatus status) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setStatus(status);
                    userRepository.save(user);

                    Map<String, String> response = new HashMap<>();
                    response.put("message", "User status updated to " + status.name());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Grants or revokes roles (e.g. ADMIN, LANGUAGE_REVIEWER). This is the only
    // way a user can obtain an elevated role — registration always assigns ROLE_USER.
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<?> updateUserRoles(@PathVariable String id, @RequestBody Set<String> roles) {
        Set<Role> resolvedRoles = new HashSet<>();
        for (String role : roles) {
            try {
                resolvedRoles.add(Role.valueOf(role.toUpperCase().startsWith("ROLE_") ? role.toUpperCase() : "ROLE_" + role.toUpperCase()));
            } catch (IllegalArgumentException e) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Error: Unknown role '" + role + "'. Valid roles: ADMIN, USER, MODERATOR, LANGUAGE_REVIEWER");
                return ResponseEntity.badRequest().body(error);
            }
        }
        resolvedRoles.add(Role.ROLE_USER);

        return userRepository.findById(id)
                .map(user -> {
                    user.setRoles(resolvedRoles);
                    UserResponse response = UserResponse.fromUser(userRepository.save(user));
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/rate-limits/search")
    public ResponseEntity<RateLimitConfigDto> getSearchRateLimits() {
        return ResponseEntity.ok(translationRateLimitService.getConfig());
    }

    @PutMapping("/rate-limits/search")
    public ResponseEntity<RateLimitConfigDto> updateSearchRateLimits(@RequestBody RateLimitConfigDto dto) {
        return ResponseEntity.ok(translationRateLimitService.updateConfig(dto));
    }

    @GetMapping("/rate-limits/users/{userId}")
    public ResponseEntity<UserRateLimitDto> getUserRateLimit(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(translationRateLimitService.getUserRateLimit(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/rate-limits/users/{userId}")
    public ResponseEntity<UserRateLimitDto> updateUserRateLimit(
            @PathVariable String userId,
            @RequestBody UserRateLimitUpdateRequest request) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(translationRateLimitService.updateUserRateLimit(user, request)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/ai/models")
    public ResponseEntity<Map<String, Object>> getAiModels() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("models", geminiMultiModelService.getModels());
        resp.put("enabled", geminiMultiModelService.isEnabled());
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/ai/models")
    public ResponseEntity<Map<String, Object>> updateAiModels(@RequestBody Map<String, Object> body) {
        if (body.containsKey("models") && body.get("models") instanceof List<?> list) {
            List<String> newModels = list.stream().map(Object::toString).toList();
            geminiMultiModelService.setModels(newModels);
        }
        if (body.containsKey("enabled") && body.get("enabled") instanceof Boolean b) {
            geminiMultiModelService.setEnabled(b);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("models", geminiMultiModelService.getModels());
        resp.put("enabled", geminiMultiModelService.isEnabled());
        resp.put("message", "AI models updated successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/ai-cache")
    public ResponseEntity<?> listAiSearchCache(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Page<AiSearchCache> cachedPage = aiSearchCacheService.listCachedEntries(page, size);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", cachedPage.getContent());
        response.put("page", cachedPage.getNumber());
        response.put("size", cachedPage.getSize());
        response.put("totalElements", cachedPage.getTotalElements());
        response.put("totalPages", cachedPage.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/ai-cache/{id}")
    public ResponseEntity<?> deleteAiCacheEntry(@PathVariable String id) {
        boolean deleted = aiSearchCacheService.deleteCacheEntry(id);
        if (deleted) {
            Map<String, String> resp = new HashMap<>();
            resp.put("message", "AI cache entry deleted successfully");
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/ai-cache/{id}/promote")
    public ResponseEntity<?> promoteAiCacheToDictionary(@PathVariable String id) {
        return aiSearchCacheService.promoteToDictionary(id, conceptRepository, translationRepository, languageRepository)
                .map(translation -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("message", "AI cached translation promoted to verified dictionary successfully");
                    resp.put("translationId", translation.getId());
                    resp.put("conceptId", translation.getConcept() != null ? translation.getConcept().getId() : null);
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
