package com.koro.app.submission.controller;

import com.koro.app.activity.service.ActivityLogService;
import com.koro.app.auth.security.CustomUserDetails;
import com.koro.app.concept.entity.Category;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.submission.entity.SubmissionStatus;
import com.koro.app.submission.entity.TranslationSubmission;
import com.koro.app.submission.repository.TranslationSubmissionRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.Role;
import com.koro.app.user.entity.User;
import com.koro.app.user.entity.UserStatus;
import com.koro.app.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerBulkApprovalTest {

    @Mock
    private TranslationSubmissionRepository submissionRepository;

    @Mock
    private TranslationRepository translationRepository;

    @Mock
    private ConceptRepository conceptRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private SubmissionController controller;

    @BeforeEach
    void setUp() {
        User adminUser = new User();
        adminUser.setId("admin-1");
        adminUser.setEmail("admin@koro.com");
        adminUser.setRoles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER));
        adminUser.setStatus(UserStatus.ACTIVE);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        CustomUserDetails.build(adminUser),
                        null,
                        List.of()
                )
        );

        when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

        Language sourceLanguage = new Language();
        sourceLanguage.setId("source-1");
        sourceLanguage.setName("Koch");
        sourceLanguage.setCode("ko");

        Language bangla = new Language();
        bangla.setId("bn-1");
        bangla.setCode("bn");
        bangla.setName("Bangla");

        Language english = new Language();
        english.setId("en-1");
        english.setCode("en");
        english.setName("English");

        when(languageRepository.findByCode("bn")).thenReturn(Optional.of(bangla));
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(english));
        when(translationRepository.findByConceptIdAndLanguageId(anyString(), anyString())).thenReturn(Optional.empty());
        when(translationRepository.save(any(Translation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> {
            Concept concept = invocation.getArgument(0);
            concept.setId("concept-1");
            return concept;
        });

        Category category = new Category();
        category.setId("category-1");
        category.setName("Nature");

        TranslationSubmission submission = new TranslationSubmission();
        submission.setId("submission-1");
        submission.setCategory(category);
        submission.setSourceLanguage(sourceLanguage);
        submission.setSourceWord("ban");
        submission.setBanglaTranslation("গাছ");
        submission.setEnglishTranslation("Tree");
        submission.setPronunciation("ban");
        submission.setNotes("Community note");
        submission.setExampleSentence("Example sentence");
        submission.setSubmittedBy(adminUser);
        submission.setStatus(SubmissionStatus.PENDING);

        when(submissionRepository.findByStatus(SubmissionStatus.PENDING)).thenReturn(List.of(submission));
        when(submissionRepository.findById("submission-1")).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(TranslationSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void adminCanApproveAllPendingSubmissions() {
        ResponseEntity<?> response = controller.approveAllPendingSubmissions();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof Map<?, ?>);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(1, body.get("approvedCount"));
        assertTrue(body.get("approvedIds") instanceof List<?> ids && ids.size() == 1 && ids.get(0).equals("submission-1"));
    }
}
