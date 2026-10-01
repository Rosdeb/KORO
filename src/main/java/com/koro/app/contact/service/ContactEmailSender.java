package com.koro.app.contact.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class ContactEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ContactEmailSender.class);

    private final String apiKey;
    private final String from;

    public ContactEmailSender(@Value("${app.resend.api-key:}") String apiKey,
                              @Value("${app.resend.from:}") String from) {
        this.apiKey = apiKey;
        this.from = from;
    }

    public void sendReply(String ticketId,
                          String toEmail,
                          String recipientName,
                          String subject,
                          String replyMessage,
                          String originalSubject,
                          String originalMessage,
                          String responderName) {

        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) {
            log.warn("Resend API key or From address not configured. Email will not be sent to: {}", toEmail);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured on the server");
        }

        try {
            String safeTicketId = (ticketId != null && !ticketId.isBlank()) ? ticketId : "KORO-INQ";
            String safeSubject = (subject != null && !subject.isBlank()) ? subject : "Response to your inquiry";
            String safeOriginalSubject = (originalSubject != null && !originalSubject.isBlank()) ? originalSubject : "(No Subject)";
            String safeName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Valued User";
            String safeOriginalMessage = (originalMessage != null && !originalMessage.isBlank()) ? originalMessage : "(No content)";
            String safeResponder = (responderName != null && !responderName.isBlank()) ? responderName : "KOROT Support Representative";

            // Anti-spam subject format: Clearly branded with ticket ref
            String emailSubject = safeSubject.startsWith("[KOROT") ? safeSubject : "[KOROT Support #" + safeTicketId + "] " + safeSubject;

            String html;
            try (var input = new ClassPathResource("templates/email/contact_reply.html").getInputStream()) {
                html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            html = html.replace("{{subject}}", escapeHtml(safeSubject))
                    .replace("{{ticketId}}", escapeHtml(safeTicketId))
                    .replace("{{name}}", escapeHtml(safeName))
                    .replace("{{recipientEmail}}", escapeHtml(toEmail))
                    .replace("{{responderName}}", escapeHtml(safeResponder))
                    .replace("{{replyMessage}}", escapeHtml(replyMessage))
                    .replace("{{originalSubject}}", escapeHtml(safeOriginalSubject))
                    .replace("{{originalMessage}}", escapeHtml(safeOriginalMessage));

            // Clean, matching plain-text email version
            String text = emailSubject + "\n\n"
                    + "Hello " + safeName + ",\n\n"
                    + "Thank you for contacting the KOROT Support Team.\n"
                    + "A member of our team has reviewed your inquiry and provided the response below:\n\n"
                    + "============================================================\n"
                    + "Response from " + safeResponder + ":\n\n"
                    + replyMessage + "\n"
                    + "============================================================\n\n"
                    + "------------------------------------------------------------\n"
                    + "YOUR ORIGINAL INQUIRY DETAILS\n"
                    + "Ticket ID: #" + safeTicketId + "\n"
                    + "Subject: " + safeOriginalSubject + "\n"
                    + "Submitted by: " + toEmail + "\n\n"
                    + "Original Message:\n"
                    + safeOriginalMessage + "\n"
                    + "------------------------------------------------------------\n\n"
                    + "If you have additional questions or need further clarification, simply reply to this email or visit https://korot.site.\n\n"
                    + "Warm regards,\n"
                    + "The KOROT Support Team\n\n"
                    + "---\n"
                    + "Why did you receive this email? You are receiving this direct communication because an inquiry was submitted to KOROT via our contact form at korot.site.\n"
                    + "Support Contact: support@korot.site | (c) 2026 KOROT Platform";

            // Determine formatted From and Reply-To
            String senderFrom = from;
            if (!senderFrom.contains("<") && !senderFrom.toLowerCase().contains("korot")) {
                senderFrom = "KOROT Support <" + from.trim() + ">";
            }

            // Extract email address for reply-to
            String replyToEmail = from;
            if (from.contains("<") && from.contains(">")) {
                replyToEmail = from.substring(from.indexOf("<") + 1, from.indexOf(">")).trim();
            }

            CreateEmailOptions.Builder emailBuilder = CreateEmailOptions.builder()
                    .from(senderFrom)
                    .to(toEmail)
                    .replyTo(replyToEmail)
                    .subject(emailSubject)
                    .html(html)
                    .text(text)
                    .addHeader("X-Entity-Ref-ID", safeTicketId);

            new Resend(apiKey).emails().send(emailBuilder.build());

            log.info("Contact reply email sent successfully to {} with Ticket #{}", toEmail, safeTicketId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Failed to send contact reply email to {}: {}", toEmail, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email: " + ex.getMessage());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
