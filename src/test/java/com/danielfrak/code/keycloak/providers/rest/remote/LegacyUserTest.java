package com.danielfrak.code.keycloak.providers.rest.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class LegacyUserTest {

    @Test
    void shouldMapToJson() throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        LegacyUser legacyUser = legacyUser();

        String result = objectMapper.writeValueAsString(legacyUser);

        String expectedJson = json();
        assertThatJson(result).isEqualTo(expectedJson);
    }

    private LegacyUser legacyUser() {
        return new LegacyUser(
                "someId",
                "someUsername",
                "someEmail",
                "someFirstName",
                "someLastName",
                true,
                false,
                Map.of("attribute1", List.of("attributeValue")),
                List.of("role1"),
                List.of("group1"),
                List.of("requiredAction1"),
                List.of(new LegacyTotp("secret", "name", 1, 2, "someAlgorithm",
                        "someEncoding")),
                List.of(new LegacyOrganization("org-1", "org-1"))
        );
    }

    private String json() {
        return """
                {
                  "id": "someId",
                  "username": "someUsername",
                  "email": "someEmail",
                  "firstName": "someFirstName",
                  "lastName": "someLastName",
                  "enabled": true,
                  "emailVerified": false,
                  "attributes": {
                    "attribute1": [
                      "attributeValue"
                    ]
                  },
                  "roles": [
                    "role1"
                  ],
                  "groups": [
                    "group1"
                  ],
                  "requiredActions": [
                    "requiredAction1"
                  ],
                  "totps": [
                    {
                      "secret": "secret",
                      "name": "name",
                      "digits": 1,
                      "period": 2,
                      "algorithm": "someAlgorithm",
                      "encoding": "someEncoding"
                    }
                  ],
                  "organizations": [
                    {
                      "orgName": "org-1",
                      "orgAlias": "org-1"
                    }
                  ]
                }
                """;
    }
}