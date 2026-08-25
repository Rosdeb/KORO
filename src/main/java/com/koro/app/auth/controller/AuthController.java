package com.koro.app.auth.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.*;
import com.koro.app.auth.entity.RefreshToken;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.auth.security.JwtUtils;
import com.koro.app.auth.service.RefreshTokenService;
import com.koro.app.user.entity.Role;
import com.koro.app.user.entity.User;
import com.koro.app.user.entity.UserStatus;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ActivityLogService activityLogService;

    // Simple cache for reset tokens in-memory for testing forgot/reset password
    private final Map<String, String> passwordResetTokens = new ConcurrentHashMap<>();

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String jwt = jwtUtils.generateJwtToken(authentication);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        // Update last login
        User user = userRepository.findById(userDetails.getId()).orElse(null);
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        }

        // Log activity
        activityLogService.logManual(user, ActivityType.LOGIN, "User logged in: " + user.getEmail());

        return ResponseEntity.ok(new JwtResponse(jwt,
                refreshToken.getToken(),
                userDetails.getId(),
                userDetails.getName(),
                userDetails.getEmail(),
                roles));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = User.builder()
                .name(signUpRequest.getName())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .nativeLanguage(signUpRequest.getNativeLanguage())
                .preferredLanguage(signUpRequest.getPreferredLanguage())
                .status(UserStatus.ACTIVE)
                .build();

        // Self-registration always grants the base USER role only. Elevated roles
        // (ADMIN, LANGUAGE_REVIEWER, MODERATOR) can only be granted afterwards by
        // an existing admin via PUT /api/v1/admin/users/{id}/roles.
        user.setRoles(Set.of(Role.ROLE_USER));
        User savedUser = userRepository.save(user);

        // Log registration
        activityLogService.logManual(savedUser, ActivityType.UPDATE_PROFILE, "User registered: " + savedUser.getEmail());

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateTokenFromUsername(user.getEmail());
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            refreshTokenService.deleteByUserId(userDetails.getId());
            User user = userRepository.findById(userDetails.getId()).orElse(null);
            activityLogService.logManual(user, ActivityType.LOGOUT, "User logged out: " + user.getEmail());
        }
        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email address not found."));
        }

        String token = UUID.randomUUID().toString();
        passwordResetTokens.put(token, request.getEmail());

        // In production, send token via email. For MVP we return it in response for testing.
        return ResponseEntity.ok(new MessageResponse("Password reset token generated successfully. For testing/API use, reset token: " + token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = passwordResetTokens.get(request.getToken());
        if (email == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid or expired reset token."));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        }

        User user = userOpt.get();
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokens.remove(request.getToken());

        return ResponseEntity.ok(new MessageResponse("Password reset successful. You can now login with your new password."));
    }
}
