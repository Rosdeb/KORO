package com.koro.app.config;

import com.koro.app.common.TextNormalizer;
import com.koro.app.concept.entity.Category;
import com.koro.app.concept.entity.Concept;
import com.koro.app.concept.repository.CategoryRepository;
import com.koro.app.concept.repository.ConceptRepository;
import com.koro.app.language.entity.Language;
import com.koro.app.language.repository.LanguageRepository;
import com.koro.app.translation.entity.Translation;
import com.koro.app.translation.repository.TranslationRepository;
import com.koro.app.user.entity.Role;
import com.koro.app.user.entity.User;
import com.koro.app.user.entity.UserStatus;
import com.koro.app.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private TranslationRepository translationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            seedDatabase();
        } catch (Exception e) {
            logger.warn("Skipping database seeding: could not reach MongoDB at startup ({})", e.getMessage());
        }
    }

    private void seedDatabase() {
        // Initialize Default Users
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .name("Koro Admin")
                    .email("admin@koro.com")
                    .password(passwordEncoder.encode("password"))
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER))
                    .build();

            User reviewer = User.builder()
                    .name("Language Reviewer")
                    .email("reviewer@koro.com")
                    .password(passwordEncoder.encode("password"))
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(Role.ROLE_LANGUAGE_REVIEWER, Role.ROLE_USER))
                    .build();

            User moderator = User.builder()
                    .name("Koro Moderator")
                    .email("moderator@koro.com")
                    .password(passwordEncoder.encode("password"))
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(Role.ROLE_MODERATOR, Role.ROLE_USER))
                    .build();

            User user = User.builder()
                    .name("John Doe")
                    .email("user@koro.com")
                    .password(passwordEncoder.encode("password"))
                    .nativeLanguage("Bangla")
                    .preferredLanguage("Chakma")
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(Role.ROLE_USER))
                    .build();

            userRepository.save(admin);
            userRepository.save(reviewer);
            userRepository.save(moderator);
            userRepository.save(user);
        }

        // Initialize Languages
        if (languageRepository.count() == 0) {
            Language english = Language.builder().name("English").nativeName("English").code("en").region("Global").active(true).build();
            Language bangla = Language.builder().name("Bangla").nativeName("বাংলা").code("bn").region("Bangladesh").active(true).build();
            Language chakma = Language.builder().name("Chakma").nativeName("চাকমা").code("ccp").region("Bangladesh / India").active(true).build();
            
            languageRepository.save(english);
            languageRepository.save(bangla);
            languageRepository.save(chakma);
        }

        // Initialize Categories
        if (categoryRepository.count() == 0) {
            Category nature = Category.builder().name("Nature").description("Words related to nature and environments").build();
            Category animals = Category.builder().name("Animals").description("Fauna and wildlife names").build();
            Category dailyLife = Category.builder().name("Daily Life").description("Conversational and everyday terms").build();

            categoryRepository.save(nature);
            categoryRepository.save(animals);
            categoryRepository.save(dailyLife);
        }

        // Initialize Concepts and Translations
        if (conceptRepository.count() == 0) {
            Category nature = categoryRepository.findByNameIgnoreCase("Nature").get();
            Category animals = categoryRepository.findByNameIgnoreCase("Animals").get();
            Category dailyLife = categoryRepository.findByNameIgnoreCase("Daily Life").get();

            Language english = languageRepository.findByCode("en").get();
            Language bangla = languageRepository.findByCode("bn").get();
            Language chakma = languageRepository.findByCode("ccp").get();

            // 1. Concept: Tree
            Concept tree = Concept.builder().name("Tree").description("A woody perennial plant").category(nature).referenceImage("tree.jpg").build();
            conceptRepository.save(tree);

            translationRepository.save(Translation.builder().concept(tree).language(english).text("Tree").pronunciation("tri:").verified(true).build());
            translationRepository.save(Translation.builder().concept(tree).language(bangla).text("গাছ").pronunciation("gach").verified(true).build());
            translationRepository.save(Translation.builder().concept(tree).language(chakma).text("বান").pronunciation("ban").verified(true).build());

            // 2. Concept: Water
            Concept water = Concept.builder().name("Water").description("A colorless, odorless liquid").category(nature).referenceImage("water.jpg").build();
            conceptRepository.save(water);

            translationRepository.save(Translation.builder().concept(water).language(english).text("Water").pronunciation("wɔːtər").verified(true).build());
            translationRepository.save(Translation.builder().concept(water).language(bangla).text("পানি").pronunciation("pani").verified(true).build());
            translationRepository.save(Translation.builder().concept(water).language(chakma).text("ভাঙ").pronunciation("bhang").verified(true).build());

            // 3. Concept: Dog
            Concept dog = Concept.builder().name("Dog").description("A common domesticated carnivorous mammal").category(animals).referenceImage("dog.jpg").build();
            conceptRepository.save(dog);

            translationRepository.save(Translation.builder().concept(dog).language(english).text("Dog").pronunciation("dɒɡ").verified(true).build());
            translationRepository.save(Translation.builder().concept(dog).language(bangla).text("কুকুর").pronunciation("kukur").verified(true).build());
            translationRepository.save(Translation.builder().concept(dog).language(chakma).text("কুই").pronunciation("kui").verified(true).build());

            // 4. Concept: House
            Concept house = Concept.builder().name("House").description("A building for human habitation").category(dailyLife).referenceImage("house.jpg").build();
            conceptRepository.save(house);

            translationRepository.save(Translation.builder().concept(house).language(english).text("House").pronunciation("haʊs").verified(true).build());
            translationRepository.save(Translation.builder().concept(house).language(bangla).text("ঘর").pronunciation("ghor").verified(true).build());
            translationRepository.save(Translation.builder().concept(house).language(chakma).text("ঘর").pronunciation("ghor").verified(true).build());
        }

        normalizeExistingTranslations();
    }

    /**
     * Brings every stored translation to the same Unicode form the search endpoint now uses.
     * Without this, rows written before {@link TextNormalizer} was introduced stay in whatever
     * form the client happened to send and Bangla searches against them keep missing.
     */
    private void normalizeExistingTranslations() {
        int fixed = 0;
        int broken = 0;
        for (Translation translation : translationRepository.findAll()) {
            // Rows missing a language or concept reference (e.g. left behind by an older code
            // path, or because the referenced document was later deleted) are silently excluded
            // from every search because the endpoints filter on language != null && concept != null.
            if (translation.getLanguage() == null || translation.getConcept() == null) {
                broken++;
                logger.warn("Translation {} (text='{}') has a missing language or concept reference and will not appear in search results",
                        translation.getId(), translation.getText());
            }

            String normalizedText = TextNormalizer.normalize(translation.getText());
            String normalizedPronunciation = TextNormalizer.normalize(translation.getPronunciation());

            boolean changed = false;
            if (normalizedText != null && !normalizedText.equals(translation.getText())) {
                translation.setText(normalizedText);
                changed = true;
            }
            if (normalizedPronunciation != null && !normalizedPronunciation.equals(translation.getPronunciation())) {
                translation.setPronunciation(normalizedPronunciation);
                changed = true;
            }
            if (changed) {
                translationRepository.save(translation);
                fixed++;
            }
        }
        if (fixed > 0) {
            logger.info("Normalized Unicode text for {} translation(s)", fixed);
        }
        if (broken > 0) {
            logger.warn("{} translation(s) are missing a language/concept reference - fix or delete them so they become searchable", broken);
        }
    }
}
