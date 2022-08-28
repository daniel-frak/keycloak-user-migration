package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserValidationDetails;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.services.UserMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = AuthenticationControllerPaths.MIGRATION_SUPPORT_PATH,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User migration")
public class UserMigrationController {

    private final UserMigrationService migrationService;

    public UserMigrationController(UserMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @GetMapping("/{usernameOrEmail}")
    @Operation(summary = "Returns data required for migrating a user to Keycloak")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User was found"),
            @ApiResponse(responseCode = "400", description = "User could not be found")})
    public ResponseEntity<UserMigrationDetails> getMigrationDetails(@PathVariable String usernameOrEmail) {
        return migrationService.getMigrationDetails(usernameOrEmail)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/{usernameOrEmail}")
    @Operation(summary = "Verifies password for given username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password is correct"),
            @ApiResponse(responseCode = "400", description = "Invalid password")})
    public ResponseEntity<?> verifyPassword(@PathVariable String usernameOrEmail,
                                            @RequestBody UserValidationDetails userValidationDetails) {
        return migrationService.passwordIsCorrect(usernameOrEmail, userValidationDetails.getPassword())
                ? ResponseEntity.ok().build()
                : ResponseEntity.badRequest().build();
    }
}
