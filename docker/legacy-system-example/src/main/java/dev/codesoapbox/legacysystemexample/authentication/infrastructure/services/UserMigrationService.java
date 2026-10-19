package dev.codesoapbox.legacysystemexample.authentication.infrastructure.services;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.TotpDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.User;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UserMigrationService {

    private final UserRepository userRepository;
    private final Map<String, List<TotpDetails>> totpsByUsername = new ConcurrentHashMap<>();

    public UserMigrationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserMigrationDetails> getMigrationDetails(String usernameOrEmail) {
        log.info("Getting migration data for: " + usernameOrEmail);
        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .map(user -> UserMigrationDetails.from(user, totpsByUsername.get(user.getUsername())));
    }

    public boolean updateTotpsForUser(String username, List<TotpDetails> totps) {
        if (userRepository.findByUsername(username).isEmpty()) {
            return false;
        }
        totpsByUsername.put(username, List.copyOf(totps));
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
