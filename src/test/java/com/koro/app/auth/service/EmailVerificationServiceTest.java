package com.koro.app.auth.service;

import com.koro.app.auth.dto.RegisterRequest;
import com.koro.app.auth.entity.EmailChallenge;
import com.koro.app.user.entity.User;
import com.koro.app.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    OtpEmailSender mail = mock(OtpEmailSender.class);
    RefreshTokenService refresh = mock(RefreshTokenService.class);
    EmailVerificationService service = new EmailVerificationService(mongo, users, encoder, mail, refresh);

    @BeforeEach void setup() {
        when(encoder.encode(anyString())).thenAnswer(i -> "hashed:" + i.getArgument(0));
    }

    @Test void registrationStoresOnlyTemporaryHashedCredentials() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("Person@Example.com"); request.setName("Person"); request.setPassword("secret123");
        service.register(request);
        ArgumentCaptor<EmailChallenge> captor = ArgumentCaptor.forClass(EmailChallenge.class);
        verify(mongo).insert(captor.capture());
        EmailChallenge challenge = captor.getValue();
        assertEquals("person@example.com", challenge.getEmail());
        assertEquals("hashed:secret123", challenge.getPendingUser().getPassword());
        assertTrue(challenge.getCodeHash().startsWith("hashed:"));
        assertTrue(challenge.getExpiresAt().isAfter(challenge.getNextSendAt()));
        verify(users, never()).insert(any(User.class));
        verify(users, never()).save(any(User.class));
        verify(mail).send(eq("person@example.com"), matches("[0-9]{6}"), eq("REGISTER"));
    }

    @Test void unknownForgotEmailDoesNotSendOrCreateChallenge() {
        service.forgotPassword("missing@example.com");
        verifyNoInteractions(mongo, mail);
    }

    @Test void missingExpiredOrExhaustedChallengeCannotCreateAccount() {
        assertThrows(ResponseStatusException.class, () -> service.verifyRegistration("a@example.com", "123456"));
        verifyNoInteractions(users);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(EmailChallenge.class));
        assertTrue(query.getValue().getQueryObject().containsKey("expiresAt"));
        assertTrue(query.getValue().getQueryObject().containsKey("attempts"));
    }

    private EmailChallenge candidate(boolean matches) {
        EmailChallenge challenge = new EmailChallenge();
        challenge.setEmail("a@example.com"); challenge.setGeneration("generation"); challenge.setCodeHash("hash");
        challenge.setPendingUser(User.builder().email("a@example.com").build());
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(EmailChallenge.class)))
                .thenReturn(challenge);
        when(encoder.matches("123456", "hash")).thenReturn(matches);
        return challenge;
    }

    @Test void wrongCodeCannotConsumeChallenge() {
        candidate(false);
        assertThrows(ResponseStatusException.class, () -> service.verifyRegistration("a@example.com", "123456"));
        verify(mongo, never()).findAndRemove(any(Query.class), eq(EmailChallenge.class));
        verifyNoInteractions(users);
    }

    @Test void correctCodeCreatesAccountOnlyOnce() {
        EmailChallenge challenge = candidate(true);
        when(mongo.findAndRemove(any(Query.class), eq(EmailChallenge.class))).thenReturn(challenge).thenReturn(null);
        when(users.insert(any(User.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("a@example.com", service.verifyRegistration("a@example.com", "123456").getEmail());
        assertThrows(ResponseStatusException.class, () -> service.verifyRegistration("a@example.com", "123456"));
        verify(users, times(1)).insert(any(User.class));
    }

    @Test void resetConsumesCodeChangesPasswordAndRevokesRefreshTokens() {
        EmailChallenge challenge = candidate(true);
        when(mongo.findAndRemove(any(Query.class), eq(EmailChallenge.class))).thenReturn(challenge);
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(User.builder().id("user1").build()));
        service.resetPassword("a@example.com", "123456", "newSecret");
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        verify(refresh).deleteByUserId("user1");
    }

    @Test void resendCooldownDoesNotSendAnotherEmail() {
        when(mongo.exists(any(Query.class), eq(EmailChallenge.class))).thenReturn(true);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.resend("a@example.com"));
        assertEquals(429, error.getStatusCode().value());
        verifyNoInteractions(mail);
    }
}
