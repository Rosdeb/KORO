package com.koro.app.config;

import com.koro.app.auth.entity.EmailChallenge;
import com.koro.app.user.entity.User;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import java.time.Duration;

@Configuration
public class EmailChallengeIndexes {
    @Bean
    ApplicationRunner emailChallengeIndexesRunner(MongoTemplate mongo) {
        return args -> {
            mongo.indexOps(EmailChallenge.class).createIndex(new Index().on("expiresAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO).named("email_challenge_expiry"));
            mongo.indexOps(User.class).createIndex(new Index().on("email", Sort.Direction.ASC).unique());
        };
    }
}
