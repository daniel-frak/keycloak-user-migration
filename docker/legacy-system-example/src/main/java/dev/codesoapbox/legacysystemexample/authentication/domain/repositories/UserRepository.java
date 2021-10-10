package dev.codesoapbox.legacysystemexample.authentication.domain.repositories;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserData;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<UserData> findByUsername(String username);

    List<UserData> findAll();
}
