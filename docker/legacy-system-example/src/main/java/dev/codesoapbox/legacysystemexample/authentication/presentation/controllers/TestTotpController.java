package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.TotpDetails;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.services.UserMigrationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("e2e")
@RequestMapping("/test-support/users")
public class TestTotpController {

    private final UserMigrationService migrationService;

    public TestTotpController(UserMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PutMapping("/{username}/totps")
    public ResponseEntity<Void> updateTotps(
            @PathVariable String username,
            @RequestBody List<TotpDetails> totps) {
        if (!migrationService.updateTotpsForUser(username, totps)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
