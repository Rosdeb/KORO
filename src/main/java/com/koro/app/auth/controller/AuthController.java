package com.koro.app.auth.controller;

import com.koro.app.activity.entity.ActivityType;
import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.dto.*;
import com.koro.app.auth.entity.RefreshToken;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.auth.security.JwtUtils;
import com.koro.app.auth.service.RefreshTokenService;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import com.koro.app.auth.service.EmailVerificationService;
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
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private EmailVerificationService verification;

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<MessageResponse> handleVerificationError(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new MessageResponse(error.getReason()));
    }

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
        verification.register(signUpRequest);
        return ResponseEntity.accepted().body(new MessageResponse("If registration is available, a verification code has been emailed. Verify within 15 minutes."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        User user = verification.verifyRegistration(request.getEmail(), request.getOtp());
        activityLogService.logManual(user, ActivityType.UPDATE_PROFILE, "User registered: " + user.getEmail());
        return ResponseEntity.ok(new MessageResponse("Email verified. You can now log in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        verification.resend(request.getEmail());
        return ResponseEntity.accepted().body(new MessageResponse("If a pending registration exists, a verification code has been emailed."));
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
        verification.forgotPassword(request.getEmail());
        return ResponseEntity.accepted().body(new MessageResponse("If an account exists, a password reset code has been emailed."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        verification.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successful. Please log in with your new password."));
    }
}
