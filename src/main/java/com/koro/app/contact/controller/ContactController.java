package com.koro.app.contact.controller;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.contact.dto.ContactMessageRequest;
import com.koro.app.contact.dto.ContactMessageResponse;
import com.koro.app.contact.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Contact", description = "Public Contact Us APIs")
public class ContactController {

    @Autowired
    private ContactService contactService;

    @PostMapping({"/contact", "/contact-us"})
    @Operation(summary = "Submit a contact us message", description = "Public endpoint for visitors and users to submit contact messages")
    public ResponseEntity<?> submitContactMessage(@Valid @RequestBody ContactMessageRequest request) {
        String userId = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            userId = userDetails.getId();
        }

        ContactMessageResponse response = contactService.submitMessage(request, userId);

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Your message has been received. We will get back to you soon.");
        body.put("contactMessage", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
