package com.koro.app.contact.controller;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.contact.dto.ContactMessageResponse;
import com.koro.app.contact.dto.ContactReplyRequest;
import com.koro.app.contact.dto.ContactStatusUpdateRequest;
import com.koro.app.contact.entity.ContactStatus;
import com.koro.app.contact.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/contact-messages")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@Tag(name = "Admin Contact Messages", description = "Admin endpoints for reviewing contact messages and replying via email")
public class AdminContactController {

    @Autowired
    private ContactService contactService;

    @GetMapping
    @Operation(summary = "List contact messages", description = "Retrieve paginated contact messages with optional status filter and search query")
    public ResponseEntity<?> getContactMessages(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactMessageResponse> messagePage = contactService.getMessages(status, search, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", messagePage.getContent());
        response.put("page", messagePage.getNumber());
        response.put("size", messagePage.getSize());
        response.put("totalElements", messagePage.getTotalElements());
        response.put("totalPages", messagePage.getTotalPages());
        response.put("hasNext", messagePage.hasNext());
        response.put("hasPrevious", messagePage.hasPrevious());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get contact message statistics", description = "Get counts for total, pending, read, replied, and archived messages")
    public ResponseEntity<?> getStatistics() {
        return ResponseEntity.ok(contactService.getStatistics());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a contact message by ID", description = "Retrieve single contact message. Automatically marks PENDING messages as READ.")
    public ResponseEntity<?> getContactMessageById(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean markRead) {
        ContactMessageResponse response = contactService.getMessageById(id, markRead);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update contact message status", description = "Update status of a contact message (e.g. READ, ARCHIVED, PENDING)")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody ContactStatusUpdateRequest request) {
        ContactMessageResponse response = contactService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reply")
    @Operation(summary = "Reply to a contact message via email", description = "Sends an email to the user's address and marks the message as REPLIED")
    public ResponseEntity<?> replyToMessage(
            @PathVariable String id,
            @Valid @RequestBody ContactReplyRequest request) {

        CustomUserDetails adminUser = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            adminUser = userDetails;
        }

        ContactMessageResponse response = contactService.replyToMessage(id, request, adminUser);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Reply sent successfully to " + response.getEmail());
        result.put("contactMessage", response);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete contact message", description = "Delete a contact message permanently (Admin only)")
    public ResponseEntity<?> deleteMessage(@PathVariable String id) {
        contactService.deleteMessage(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Contact message deleted successfully");
        return ResponseEntity.ok(response);
    }
}
