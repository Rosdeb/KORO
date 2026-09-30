package com.koro.app.auth.service;

import com.resend.Resend;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class OtpEmailSender {
    private final String apiKey;
    private final String from;

    public OtpEmailSender(@Value("${app.resend.api-key:}") String apiKey,
                          @Value("${app.resend.from:}") String from) {
        this.apiKey = apiKey;
        this.from = from;
    }

    public void send(String email, String code, String purpose) {
        if (apiKey.isBlank() || from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured");
        }
        try {
            if (code == null || !code.matches("[0-9]{6}")) {
                throw new IllegalArgumentException("Expected a six-digit verification code");
            }
            boolean registration = "REGISTER".equals(purpose);
            String title = registration ? "Verify your email address" : "Reset your password";
            String intro = registration
                    ? "Welcome to KOROT! Confirm your email address to finish creating your account."
                    : "We received a request to reset your KOROT password. Use the code below to choose a new password.";
            String instruction = registration
                    ? "Enter this six-digit code on the email verification screen in KOROT."
                    : "Return to the password reset screen in KOROT and enter this code along with your new password.";
            String ignore = registration
                    ? "Did not sign up for KOROT? You can ignore this email. Your unverified registration will expire automatically."
                    : "Did not request a password reset? You can ignore this email. Your password has not been changed.";
            String html;
            try (var input = new ClassPathResource("templates/email/otp.html").getInputStream()) {
                html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            // All substitutions are fixed copy or a validated numeric code, never user-supplied HTML.
            html = html.replace("{{title}}", title)
                    .replace("{{preheader}}", registration
                            ? "One more step to get started: verify your email with your KOROT code."
                            : "Your KOROT password reset code is ready. Keep it private.")
                    .replace("{{label}}", registration ? "Welcome to KOROT" : "Account security")
                    .replace("{{intro}}", intro).replace("{{instruction}}", instruction)
                    .replace("{{ignore}}", ignore).replace("{{code}}", code);
            String text = title + "\n\nHi there,\n\n" + intro
                    + "\n\nYour verification code: " + code + "\n\n" + instruction
                    + "\n\nThe verification window lasts 15 minutes from your first request. "
                    + "Resending a code does not extend it. Only your most recent code will work."
                    + "\n\nKeep this code private. Never share it with anyone.\n\n" + ignore
                    + "\n\nThanks,\nThe KOROT team\n\nThis is an automated security email from KOROT.";
            new Resend(apiKey).emails().send(CreateEmailOptions.builder()
                    .from(from).to(email)
                    .subject(purpose.equals("REGISTER") ? "Verify your KOROT email" : "Reset your KOROT password")
                    .html(html)
                    .text(text)
                    .build());
        } catch (Exception ex) {
            // Do not expose provider responses, credentials, or OTPs.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email could not be sent. Please try again later.");
        }
    }
}
