package dev.codesoapbox.legacysystemexample.authentication.domain.repositories;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    List<User> findAll();

    Optional<User> findByEmail(String email);
}
