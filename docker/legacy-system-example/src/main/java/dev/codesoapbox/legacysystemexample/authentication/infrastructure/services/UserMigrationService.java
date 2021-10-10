package dev.codesoapbox.legacysystemexample.authentication.infrastructure.services;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserData;
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

    public Optional<UserMigrationDetails> getMigrationDetails(String username) {
        log.info("Getting migration data for: " + username);
        Optional<UserData> user = userRepository.findByUsername(username);

        return user.map(UserMigrationDetails::from);
    }

    public boolean passwordIsCorrect(String username, String password) {
        log.info("Verifying password for: " + username);
        Optional<UserData> user = userRepository.findByUsername(username);

        return user.map(u -> Objects.equals(u.getPassword(), password))
                .orElse(false);
    }
}
