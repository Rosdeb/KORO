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

    public void sendReply(String toEmail, String recipientName, String subject, String replyMessage, String originalMessage) {
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank()) {
            log.warn("Resend API key or From address not configured. Email will not be sent to: {}", toEmail);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured on the server");
        }

        try {
            String finalSubject = (subject != null && !subject.isBlank()) ? subject : "Response to your inquiry - KOROT Support";
            String safeName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Valued User";
            String safeOriginal = (originalMessage != null) ? originalMessage : "(No content)";

            String html;
            try (var input = new ClassPathResource("templates/email/contact_reply.html").getInputStream()) {
                html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            html = html.replace("{{subject}}", escapeHtml(finalSubject))
                    .replace("{{name}}", escapeHtml(safeName))
                    .replace("{{replyMessage}}", escapeHtml(replyMessage))
                    .replace("{{originalMessage}}", escapeHtml(safeOriginal));

            String text = finalSubject + "\n\n"
                    + "Hello " + safeName + ",\n\n"
                    + "Thank you for contacting us. Here is our response to your inquiry:\n\n"
                    + replyMessage + "\n\n"
                    + "----------------------------------------\n"
                    + "Your Original Message:\n"
                    + safeOriginal + "\n"
                    + "----------------------------------------\n\n"
                    + "Best regards,\nThe KOROT Team\n\n"
                    + "This is an official response from KOROT.";

            new Resend(apiKey).emails().send(CreateEmailOptions.builder()
                    .from(from)
                    .to(toEmail)
                    .subject(finalSubject)
                    .html(html)
                    .text(text)
                    .build());

            log.info("Contact reply email sent successfully to {}", toEmail);
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
