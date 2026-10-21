package dev.codesoapbox.legacysystemexample.authentication.infrastructure.services;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.TotpDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.User;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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

    public boolean updateTotpsForUser(String username, List<TotpDetails> totps) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return false;
        }
        user.get().setTotps(List.copyOf(totps));
        return true;
    }

    public boolean passwordIsCorrect(String usernameOrEmail, String password) {
        log.info("Verifying password for: " + usernameOrEmail);

        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .map(u -> Objects.equals(u.getPassword(), password))
                .orElse(false);
    }
}
