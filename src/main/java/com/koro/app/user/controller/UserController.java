package com.koro.app.user.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.MessageResponse;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.user.dto.ChangePasswordRequest;
import com.koro.app.user.dto.ProfileUpdateRequest;
import com.koro.app.user.dto.UserResponse;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(new MessageResponse("Unauthorized"));
        }
        return ResponseEntity.ok(UserResponse.fromUser(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(new MessageResponse("Unauthorized"));
        }

        if (request.getName() != null) user.setName(request.getName());
        if (request.getProfileImage() != null) user.setProfileImage(request.getProfileImage());
        if (request.getNativeLanguage() != null) user.setNativeLanguage(request.getNativeLanguage());
        if (request.getPreferredLanguage() != null) user.setPreferredLanguage(request.getPreferredLanguage());

        User updatedUser = userRepository.save(user);
        activityLogService.log(ActivityType.UPDATE_PROFILE, "Updated profile fields", updatedUser.getId(), null);

        return ResponseEntity.ok(UserResponse.fromUser(updatedUser));
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(new MessageResponse("Unauthorized"));
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Incorrect old password"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        activityLogService.log(ActivityType.UPDATE_PROFILE, "Changed account password", user.getId(), null);

        return ResponseEntity.ok(new MessageResponse("Password updated successfully!"));
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId()).orElse(null);
        }
        return null;
    }
}
