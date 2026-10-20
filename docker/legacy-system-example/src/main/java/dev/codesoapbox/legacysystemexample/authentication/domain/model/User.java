package dev.codesoapbox.legacysystemexample.authentication.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Builder
public class User {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private List<String> requiredActions;
    @Setter
    private List<TotpDetails> totps;
}
