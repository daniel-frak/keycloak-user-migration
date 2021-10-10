package dev.codesoapbox.legacysystemexample.authentication.presentation.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.TestUserDataProvider;
import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserData;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WebMvcTest(value = UserController.class)
public class UserControllerTest {

    private final static String PATH = "/" + AuthenticationControllerPaths.USERS_PATH;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserController controller;

    @MockBean
    private UserRepository userRepository;

    private TestUserDataProvider userDataProvider;

    @BeforeEach
    void setUp() {
        userDataProvider = new TestUserDataProvider();
    }

    @Test
    void contextLoads() {
        assertNotNull(controller);
    }

    @Test
    void shouldReturnAllUsers() throws Exception {
        List<UserData> users = List.of(userDataProvider.full());

        when(userRepository.findAll())
                .thenReturn(users);

        String expectedResponse = objectMapper.writeValueAsString(users);

        mvc.perform(get(PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedResponse));
    }
}
