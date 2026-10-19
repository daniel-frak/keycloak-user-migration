package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.TotpDetails;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.services.UserMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WebMvcTest(TestTotpController.class)
@ActiveProfiles("e2e")
class TestTotpControllerTest {

    private static final String PATH = "/test-support/users/lucy/totps";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserMigrationService migrationService;

    @Test
    void shouldUpdateTotpsForKnownUser() throws Exception {
        var totps = List.of(new TotpDetails(
                "ONSWG4TFOQ======", "E2E authenticator", 6, 30, "HmacSHA1", "BASE32"));
        when(migrationService.updateTotpsForUser("lucy", totps)).thenReturn(true);

        mvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(totps)))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNotFoundForUnknownUser() throws Exception {
        when(migrationService.updateTotpsForUser("lucy", List.of())).thenReturn(false);

        mvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNotFound());
    }
}
