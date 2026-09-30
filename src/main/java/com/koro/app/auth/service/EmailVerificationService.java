package com.koro.app.auth.service;

import com.koro.app.auth.dto.RegisterRequest;
import com.koro.app.auth.entity.EmailChallenge;
import com.koro.app.user.entity.*;
import com.koro.app.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final OtpEmailSender mail;
    private final RefreshTokenService refreshTokens;

    public EmailVerificationService(MongoTemplate mongo, UserRepository users, PasswordEncoder encoder,
                                    OtpEmailSender mail, RefreshTokenService refreshTokens) {
        this.mongo = mongo;
        this.users = users;
        this.encoder = encoder;
        this.mail = mail;
        this.refreshTokens = refreshTokens;
    }

    public static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    public void register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        if (users.findByEmailIgnoreCase(email).isPresent()) return;
        User pending = User.builder().name(request.getName()).email(email)
                .password(encoder.encode(request.getPassword()))
                .nativeLanguage(request.getNativeLanguage()).preferredLanguage(request.getPreferredLanguage())
                .roles(Set.of(Role.ROLE_USER)).status(UserStatus.ACTIVE).build();
        issue(email, "REGISTER", pending, false);
    }

    public void resend(String email) { issue(normalize(email), "REGISTER", null, true); }

    public void forgotPassword(String email) {
        email = normalize(email);
        if (users.findByEmailIgnoreCase(email).isPresent()) issue(email, "RESET", null, false);
    }

    private void issue(String email, String purpose, User pending, boolean resendOnly) {
        Instant now = Instant.now();
        String id = purpose + ":" + email;
        // Physical TTL deletion is asynchronous; remove expired rows before reusing the identity.
        mongo.remove(Query.query(Criteria.where("_id").is(id).and("expiresAt").lte(now)), EmailChallenge.class);
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String generation = UUID.randomUUID().toString();
        Update update = new Update().set("codeHash", encoder.encode(code)).set("generation", generation)
                .set("nextSendAt", now.plusSeconds(60)).inc("sends", 1);
        // Resends preserve the original registration payload, deadline, and attempt budget.
        EmailChallenge challenge = mongo.findAndModify(Query.query(Criteria.where("_id").is(id)
                        .and("expiresAt").gt(now).and("nextSendAt").lte(now)
                        .and("attempts").lt(5).and("sends").lt(5)), update,
                FindAndModifyOptions.options().returnNew(true), EmailChallenge.class);
        if (challenge == null) {
            if (mongo.exists(Query.query(Criteria.where("_id").is(id)), EmailChallenge.class)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Wait before requesting another code. At most five sends and five attempts are allowed per 15 minutes.");
            }
            if (resendOnly) return;
            challenge = new EmailChallenge();
            challenge.setId(id); challenge.setEmail(email); challenge.setPurpose(purpose);
            challenge.setCodeHash(encoder.encode(code)); challenge.setGeneration(generation);
            challenge.setExpiresAt(now.plusSeconds(900)); challenge.setNextSendAt(now.plusSeconds(60));
            challenge.setSends(1); challenge.setPendingUser(pending);
            try { mongo.insert(challenge); }
            catch (DuplicateKeyException ex) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before retrying");
            }
        }
        mail.send(email, code, purpose);
    }

    public User verifyRegistration(String email, String code) {
        EmailChallenge challenge = consume(email, code, "REGISTER");
        try { return users.insert(challenge.getPendingUser()); }
        catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists. Please log in.");
        }
    }

    public void resetPassword(String email, String code, String newPassword) {
        EmailChallenge challenge = consume(email, code, "RESET");
        User user = users.findByEmailIgnoreCase(challenge.getEmail()).orElseThrow(this::invalid);
        // Update only the password so concurrent profile/role edits are not overwritten.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId())),
                new Update().set("password", encoder.encode(newPassword)), User.class);
        refreshTokens.deleteByUserId(user.getId());
    }

    private EmailChallenge consume(String email, String code, String purpose) {
        String id = purpose + ":" + normalize(email);
        EmailChallenge challenge = mongo.findAndModify(Query.query(Criteria.where("_id").is(id)
                        .and("expiresAt").gt(Instant.now()).and("attempts").lt(5)),
                new Update().inc("attempts", 1), FindAndModifyOptions.options().returnNew(true), EmailChallenge.class);
        if (challenge == null || !encoder.matches(code, challenge.getCodeHash())) throw invalid();
        // Atomic removal makes a code single-use, even with concurrent verification requests.
        EmailChallenge consumed = mongo.findAndRemove(Query.query(Criteria.where("_id").is(id)
                .and("generation").is(challenge.getGeneration()).and("expiresAt").gt(Instant.now())), EmailChallenge.class);
        if (consumed == null) throw invalid();
        return consumed;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
    }
}
