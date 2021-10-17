package dev.codesoapbox.legacysystemexample.authentication.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class UserMigrationDetails {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private boolean emailVerified;
    private Map<String, List<String>> attributes;
    private List<String> roles;
    private List<String> groups;
    private List<String> requiredActions;

    public static UserMigrationDetails from(User user) {
        return UserMigrationDetails.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(true)
                .emailVerified(true)
                .attributes(Map.of("attribute 1", List.of("value1", "value 2")))
                .roles(List.of("role 1", "role 2"))
                .groups(List.of("group 1", "group2"))
                .requiredActions(List.of("UPDATE_PROFILE"))
                .build();
    }
}
