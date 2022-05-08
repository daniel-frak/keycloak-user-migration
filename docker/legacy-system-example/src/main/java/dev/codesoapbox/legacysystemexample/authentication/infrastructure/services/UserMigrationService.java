package dev.codesoapbox.legacysystemexample.authentication.infrastructure.services;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.User;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class UserMigrationService {

    private final UserRepository userRepository;

    public UserMigrationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserMigrationDetails> getMigrationDetails(String usernameOrEmail) {
        log.info("Getting migration data for: " + usernameOrEmail);
        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .map(UserMigrationDetails::from);
    }

    public boolean passwordIsCorrect(String usernameOrEmail, String password) {
        log.info("Verifying password for: " + usernameOrEmail);

        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .map(u -> Objects.equals(u.getPassword(), password))
                .orElse(false);
    }
}
