package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.TestMigrationDetailsProvider;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserMigrationDetails;
import dev.codesoapbox.legacysystemexample.authentication.infrastructure.services.UserMigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WebMvcTest(value = UserMigrationController.class)
class UserMigrationControllerTest {

    private final static String PATH = "/" + AuthenticationControllerPaths.MIGRATION_SUPPORT_PATH;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMigrationController controller;

    @MockBean
    private UserMigrationService migrationService;

    private TestMigrationDetailsProvider detailsProvider;

    @BeforeEach
    void setUp() {
        detailsProvider = new TestMigrationDetailsProvider();
    }

    @Test
    void contextLoads() {
        assertNotNull(controller);
    }

    @Test
    void shouldReturnUserMigrationDetails() throws Exception {
        UserMigrationDetails details = detailsProvider.full();
        String username = details.getUsername();

        when(migrationService.getMigrationDetails(username))
                .thenReturn(Optional.of(details));

        String expectedResponse = objectMapper.writeValueAsString(details);

        mvc.perform(get(PATH + "/" + username)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }

    @Test
    void shouldReturnNotFoundWhenNonExistentUsername() throws Exception {
        when(migrationService.getMigrationDetails("non_existent"))
                .thenReturn(Optional.empty());

        mvc.perform(get(PATH + "/non_existent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldVerifyCorrectPassword() throws Exception {
        String content = objectMapper.writeValueAsString("password");

        when(migrationService.passwordIsCorrect("username", "password"))
                .thenReturn(true);

        mvc.perform(post(PATH + "/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());
    }

    @Test
    void shouldVerifyIncorrectPassword() throws Exception {
        String content = objectMapper.writeValueAsString("password");

        when(migrationService.passwordIsCorrect("username", "password"))
                .thenReturn(false);

        mvc.perform(post(PATH + "/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());
    }
}