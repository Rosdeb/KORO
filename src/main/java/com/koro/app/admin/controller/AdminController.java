package com.koro.app.admin.controller;

import com.koro.app.activity.repository.ActivityLogRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.translation.repository.TranslationRepository;
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
}
