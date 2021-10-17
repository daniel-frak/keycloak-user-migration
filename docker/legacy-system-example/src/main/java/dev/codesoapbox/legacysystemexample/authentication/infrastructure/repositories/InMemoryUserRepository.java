package dev.codesoapbox.legacysystemexample.authentication.infrastructure.repositories;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.User;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {

    private final List<User> users;

    public InMemoryUserRepository() {
        this.users = List.of(
                generateUser("Lucy", "Brennan"),
                generateUser("Mark", "Brown"),
                generateUser("Kate", "Thomson"),
                generateUser("John", "Doe")
        );
    }

    private User generateUser(String name, String lastName) {
        String username = name.toLowerCase(Locale.ROOT);

        return User.builder()
                .username(username)
                .email(username + "@localhost.com")
                .firstName(name)
                .lastName(lastName)
                .password("password")
                .build();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return this.users;
    }
}
