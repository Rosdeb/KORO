package com.koro.app.contact.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koro.app.auth.security.CustomUserDetails;
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
class AdminContactControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContactService contactService;

    @InjectMocks
    private AdminContactController adminContactController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContactMessageResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminContactController).build();

        CustomUserDetails adminUser = new CustomUserDetails(
                "admin-1", "Admin", "admin@korot.site", "pass", List.of(), true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, List.of())
        );

        sampleResponse = ContactMessageResponse.builder()
                .id("contact-1")
                .name("Jane Doe")
                .email("jane@example.com")
                .subject("Inquiry")
                .message("Need help with contributing dictionary words.")
                .status(ContactStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetContactMessages() throws Exception {
        when(contactService.getMessages(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/admin/contact-messages")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("contact-1"));
    }

    @Test
    void testGetStatistics() throws Exception {
        when(contactService.getStatistics()).thenReturn(Map.of(
                "total", 5L,
                "pending", 2L,
                "read", 1L,
                "replied", 2L,
                "archived", 0L
        ));

        mockMvc.perform(get("/api/v1/admin/contact-messages/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.pending").value(2));
    }

    @Test
    void testGetContactMessageById() throws Exception {
        when(contactService.getMessageById(eq("contact-1"), eq(true))).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/admin/contact-messages/contact-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("contact-1"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void testUpdateStatus() throws Exception {
        ContactStatusUpdateRequest req = ContactStatusUpdateRequest.builder()
                .status(ContactStatus.ARCHIVED)
                .build();

        ContactMessageResponse updated = ContactMessageResponse.builder()
                .id("contact-1")
                .status(ContactStatus.ARCHIVED)
                .build();

        when(contactService.updateStatus(eq("contact-1"), any(ContactStatusUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/contact-messages/contact-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void testReplyToMessage() throws Exception {
        ContactReplyRequest req = ContactReplyRequest.builder()
                .subject("Re: Inquiry")
                .message("Thank you! Please check our contributor guide.")
                .build();

        ContactMessageResponse replied = ContactMessageResponse.builder()
                .id("contact-1")
                .email("jane@example.com")
                .status(ContactStatus.REPLIED)
                .replySubject("Re: Inquiry")
                .replyMessage("Thank you! Please check our contributor guide.")
                .repliedBy("Admin")
                .build();

        when(contactService.replyToMessage(eq("contact-1"), any(ContactReplyRequest.class), any())).thenReturn(replied);

        mockMvc.perform(post("/api/v1/admin/contact-messages/contact-1/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reply sent successfully to jane@example.com"))
                .andExpect(jsonPath("$.contactMessage.status").value("REPLIED"));
    }

    @Test
    void testDeleteMessage() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/contact-messages/contact-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contact message deleted successfully"));

        verify(contactService).deleteMessage("contact-1");
    }
}
