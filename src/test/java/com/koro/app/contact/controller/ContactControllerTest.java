package com.koro.app.contact.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.contact.dto.ContactMessageRequest;
import com.koro.app.contact.dto.ContactMessageResponse;
import com.koro.app.contact.dto.ContactReplyRequest;
import com.koro.app.contact.dto.ContactStatusUpdateRequest;
import com.koro.app.contact.entity.ContactStatus;
import com.koro.app.contact.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private ContactController contactController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contactController).build();
    }

    @Test
    void testSubmitContactMessage_Success() throws Exception {
        ContactMessageRequest request = ContactMessageRequest.builder()
                .name("Alex Smith")
                .email("alex@example.com")
                .subject("Website feedback")
                .message("Great work on indigenous language archiving!")
                .build();

        ContactMessageResponse mockResponse = ContactMessageResponse.builder()
                .id("contact-1")
                .name("Alex Smith")
                .email("alex@example.com")
                .subject("Website feedback")
                .message("Great work on indigenous language archiving!")
                .status(ContactStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(contactService.submitMessage(any(ContactMessageRequest.class), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Your message has been received. We will get back to you soon."))
                .andExpect(jsonPath("$.contactMessage.id").value("contact-1"))
                .andExpect(jsonPath("$.contactMessage.name").value("Alex Smith"));
    }

    @Test
    void testSubmitContactMessage_ValidationFailure() throws Exception {
        ContactMessageRequest invalidRequest = ContactMessageRequest.builder()
                .name("")
                .email("invalid-email")
                .message("")
                .build();

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
