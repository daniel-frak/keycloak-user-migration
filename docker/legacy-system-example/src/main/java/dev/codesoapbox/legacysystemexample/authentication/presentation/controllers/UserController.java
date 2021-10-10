package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserData;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = AuthenticationControllerPaths.USERS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User debug", description = "Not required by the Keycloak migration plugin, created to help with debugging")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping()
    @Operation(summary = "Helper endpoint that returns all available test users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Complete user list")})
    public ResponseEntity<List<UserData>> getTestUsers() {
        var testUsers = userRepository.findAll();

        return ResponseEntity.ok(testUsers);
    }
}
