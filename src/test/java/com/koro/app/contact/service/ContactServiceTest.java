package com.koro.app.contact.service;

import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.contact.dto.ContactMessageRequest;
import com.koro.app.contact.dto.ContactMessageResponse;
import com.koro.app.contact.dto.ContactReplyRequest;
import com.koro.app.contact.dto.ContactStatusUpdateRequest;
import com.koro.app.contact.entity.ContactMessage;
import com.koro.app.contact.entity.ContactStatus;
import com.koro.app.contact.repository.ContactMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;

    @Mock
    private ContactEmailSender contactEmailSender;

    @InjectMocks
    private ContactService contactService;

    private ContactMessage sampleMessage;

    @BeforeEach
    void setUp() {
        sampleMessage = ContactMessage.builder()
                .id("msg-123")
                .name("Jane Doe")
                .email("jane@example.com")
                .subject("Question about language support")
                .message("Do you support Santali language?")
                .status(ContactStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testSubmitMessage_Success() {
        ContactMessageRequest request = ContactMessageRequest.builder()
                .name("  Jane Doe  ")
                .email("Jane@Example.COM  ")
                .subject("  Support query  ")
                .message("  Hello, this is a test message.  ")
                .build();

        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> {
            ContactMessage m = i.getArgument(0);
            m.setId("saved-id-1");
            return m;
        });

        ContactMessageResponse response = contactService.submitMessage(request, "user-abc");

        assertNotNull(response);
        assertEquals("saved-id-1", response.getId());
        assertEquals("Jane Doe", response.getName());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals("Support query", response.getSubject());
        assertEquals("Hello, this is a test message.", response.getMessage());
        assertEquals(ContactStatus.PENDING, response.getStatus());
        assertEquals("user-abc", response.getUserId());

        verify(contactMessageRepository).save(any(ContactMessage.class));
    }

    @Test
    void testGetMessages_WithStatusAndSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContactMessage> page = new PageImpl<>(List.of(sampleMessage));

        when(contactMessageRepository.searchByStatus(eq("Santali"), eq(ContactStatus.PENDING), eq(pageable)))
                .thenReturn(page);

        Page<ContactMessageResponse> result = contactService.getMessages(ContactStatus.PENDING, "Santali", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Jane Doe", result.getContent().get(0).getName());
    }

    @Test
    void testGetMessages_All() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContactMessage> page = new PageImpl<>(List.of(sampleMessage));

        when(contactMessageRepository.findAll(eq(pageable))).thenReturn(page);

        Page<ContactMessageResponse> result = contactService.getMessages(null, null, pageable);

        assertEquals(1, result.getTotalElements());
        verify(contactMessageRepository).findAll(pageable);
    }

    @Test
    void testGetMessageById_MarksReadWhenPending() {
        when(contactMessageRepository.findById("msg-123")).thenReturn(Optional.of(sampleMessage));
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));

        ContactMessageResponse response = contactService.getMessageById("msg-123", true);

        assertEquals(ContactStatus.READ, response.getStatus());
        verify(contactMessageRepository).save(sampleMessage);
    }

    @Test
    void testGetMessageById_NotFound() {
        when(contactMessageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> contactService.getMessageById("missing", true));
    }

    @Test
    void testUpdateStatus() {
        when(contactMessageRepository.findById("msg-123")).thenReturn(Optional.of(sampleMessage));
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));

        ContactStatusUpdateRequest req = ContactStatusUpdateRequest.builder()
                .status(ContactStatus.ARCHIVED)
                .build();

        ContactMessageResponse response = contactService.updateStatus("msg-123", req);

        assertEquals(ContactStatus.ARCHIVED, response.getStatus());
    }

    @Test
    void testReplyToMessage_Success() {
        when(contactMessageRepository.findById("msg-123")).thenReturn(Optional.of(sampleMessage));
        when(contactMessageRepository.save(any(ContactMessage.class))).thenAnswer(i -> i.getArgument(0));

        CustomUserDetails adminUser = new CustomUserDetails(
                "admin-id", "Admin User", "admin@korot.site", "password", List.of(), true
        );

        ContactReplyRequest replyRequest = ContactReplyRequest.builder()
                .subject("Re: Santali language question")
                .message("Yes, we do support Santali!")
                .build();

        ContactMessageResponse response = contactService.replyToMessage("msg-123", replyRequest, adminUser);

        verify(contactEmailSender).sendReply(
                eq("msg-123"),
                eq("jane@example.com"),
                eq("Jane Doe"),
                eq("Re: Santali language question"),
                eq("Yes, we do support Santali!"),
                eq("Question about language support"),
                eq("Do you support Santali language?"),
                eq("Admin User")
        );

        assertEquals(ContactStatus.REPLIED, response.getStatus());
        assertEquals("Re: Santali language question", response.getReplySubject());
        assertEquals("Yes, we do support Santali!", response.getReplyMessage());
        assertEquals("Admin User", response.getRepliedBy());
        assertEquals("admin-id", response.getRepliedByUserId());
        assertNotNull(response.getRepliedAt());
    }

    @Test
    void testDeleteMessage() {
        when(contactMessageRepository.existsById("msg-123")).thenReturn(true);

        contactService.deleteMessage("msg-123");

        verify(contactMessageRepository).deleteById("msg-123");
    }

    @Test
    void testGetStatistics() {
        when(contactMessageRepository.count()).thenReturn(10L);
        when(contactMessageRepository.countByStatus(ContactStatus.PENDING)).thenReturn(4L);
        when(contactMessageRepository.countByStatus(ContactStatus.READ)).thenReturn(2L);
        when(contactMessageRepository.countByStatus(ContactStatus.REPLIED)).thenReturn(3L);
        when(contactMessageRepository.countByStatus(ContactStatus.ARCHIVED)).thenReturn(1L);

        Map<String, Object> stats = contactService.getStatistics();

        assertEquals(10L, stats.get("total"));
        assertEquals(4L, stats.get("pending"));
        assertEquals(2L, stats.get("read"));
        assertEquals(3L, stats.get("replied"));
        assertEquals(1L, stats.get("archived"));
    }
}
