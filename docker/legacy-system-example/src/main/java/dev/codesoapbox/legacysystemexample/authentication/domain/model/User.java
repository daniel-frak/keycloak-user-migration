package dev.codesoapbox.legacysystemexample.authentication.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class User {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private List<String> requiredActions;
}
