package com.danielfrak.code.keycloak.providers.rest.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class UserPasswordDtoTest {

    @Test
    void shouldMapToJson() throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        UserPasswordDto userPasswordDto = userPasswordDto();

        String result = objectMapper.writeValueAsString(userPasswordDto);

        String expectedJson = json();
        assertThatJson(result).isEqualTo(expectedJson);
    }

    private UserPasswordDto userPasswordDto() {
        return new UserPasswordDto("somePassword");
    }

    private String json() {
        return """
                {
                    "password": "somePassword"
                }
                """;
    }
}