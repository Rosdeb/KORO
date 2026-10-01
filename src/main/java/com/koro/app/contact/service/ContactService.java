package com.koro.app.contact.service;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.common.TextNormalizer;
import com.koro.app.contact.dto.ContactMessageRequest;
import com.koro.app.contact.dto.ContactMessageResponse;
import com.koro.app.contact.dto.ContactReplyRequest;
import com.koro.app.contact.dto.ContactStatusUpdateRequest;
import com.koro.app.contact.entity.ContactMessage;
import com.koro.app.contact.entity.ContactStatus;
import com.koro.app.contact.repository.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ContactService {

    @Autowired
    private ContactMessageRepository contactMessageRepository;

    @Autowired
    private ContactEmailSender contactEmailSender;

    public ContactMessageResponse submitMessage(ContactMessageRequest request, String userId) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = request.getName().trim();
        String subject = request.getSubject() != null ? request.getSubject().trim() : null;
        String message = request.getMessage().trim();

        ContactMessage contactMessage = ContactMessage.builder()
                .name(name)
                .email(email)
                .subject(subject)
                .message(message)
                .userId(userId)
                .status(ContactStatus.PENDING)
                .build();

        ContactMessage saved = contactMessageRepository.save(contactMessage);
        return ContactMessageResponse.fromEntity(saved);
    }

    public Page<ContactMessageResponse> getMessages(ContactStatus status, String search, Pageable pageable) {
        Page<ContactMessage> page;
        boolean hasSearch = search != null && !search.trim().isEmpty();

        if (hasSearch) {
            String query = search.trim();
            if (status != null) {
                page = contactMessageRepository.searchByStatus(query, status, pageable);
            } else {
                page = contactMessageRepository.search(query, pageable);
            }
        } else {
            if (status != null) {
                page = contactMessageRepository.findByStatus(status, pageable);
            } else {
                page = contactMessageRepository.findAll(pageable);
            }
        }

        return page.map(ContactMessageResponse::fromEntity);
    }

    public ContactMessageResponse getMessageById(String id, boolean markReadIfPending) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message not found with id: " + id));

        if (markReadIfPending && message.getStatus() == ContactStatus.PENDING) {
            message.setStatus(ContactStatus.READ);
            message = contactMessageRepository.save(message);
        }

        return ContactMessageResponse.fromEntity(message);
    }

    public ContactMessageResponse updateStatus(String id, ContactStatusUpdateRequest request) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message not found with id: " + id));

        message.setStatus(request.getStatus());
        ContactMessage saved = contactMessageRepository.save(message);
        return ContactMessageResponse.fromEntity(saved);
    }

    public ContactMessageResponse replyToMessage(String id, ContactReplyRequest request, CustomUserDetails adminUser) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message not found with id: " + id));

        String subject = (request.getSubject() != null && !request.getSubject().isBlank())
                ? request.getSubject().trim()
                : (message.getSubject() != null && !message.getSubject().isBlank()
                    ? "Re: " + message.getSubject().trim()
                    : "Response to your inquiry - KOROT Support");

        String replyContent = request.getMessage().trim();

        // 1. Send the actual email via Resend
        contactEmailSender.sendReply(
                message.getEmail(),
                message.getName(),
                subject,
                replyContent,
                message.getMessage()
        );

        // 2. Record reply in database
        message.setReplySubject(subject);
        message.setReplyMessage(replyContent);
        message.setRepliedAt(LocalDateTime.now());
        message.setStatus(ContactStatus.REPLIED);

        if (adminUser != null) {
            String adminName = (adminUser.getName() != null && !adminUser.getName().isBlank())
                    ? adminUser.getName()
                    : adminUser.getEmail();
            message.setRepliedBy(adminName);
            message.setRepliedByUserId(adminUser.getId());
        }

        ContactMessage saved = contactMessageRepository.save(message);
        return ContactMessageResponse.fromEntity(saved);
    }

    public void deleteMessage(String id) {
        if (!contactMessageRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact message not found with id: " + id);
        }
        contactMessageRepository.deleteById(id);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", contactMessageRepository.count());
        stats.put("pending", contactMessageRepository.countByStatus(ContactStatus.PENDING));
        stats.put("read", contactMessageRepository.countByStatus(ContactStatus.READ));
        stats.put("replied", contactMessageRepository.countByStatus(ContactStatus.REPLIED));
        stats.put("archived", contactMessageRepository.countByStatus(ContactStatus.ARCHIVED));
        return stats;
    }
}
