package dev.codesoapbox.legacysystemexample.authentication.domain.model;

import java.util.List;
import java.util.Map;

public class TestMigrationDetailsProvider {

    public UserMigrationDetails full() {
        return UserMigrationDetails.builder()
                .id("123")
                .username("username")
                .email("username@email.com")
                .firstName("Name")
                .lastName("LastName")
                .enabled(true)
                .emailVerified(true)
                .attributes(Map.of("attribute 1", List.of("value1", "value 2")))
                .roles(List.of("role 1", "role 2"))
                .groups(List.of("group 1", "group2"))
                .requiredActions(List.of("UPDATE_PROFILE"))
                .build();
    }
}
