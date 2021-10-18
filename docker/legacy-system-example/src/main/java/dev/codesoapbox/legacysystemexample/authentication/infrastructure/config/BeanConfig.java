package dev.codesoapbox.legacysystemexample.authentication.infrastructure.config;

import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.repositories.InMemoryUserRepository;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.services.UserMigrationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    UserMigrationService userMigrationService(UserRepository userRepository) {
        return new UserMigrationService(userRepository);
    }

    @Bean
    UserRepository userRepository() {
        return new InMemoryUserRepository();
    }
}
