package dev.codesoapbox.legacysystemexample.authentication.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserData {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
}
