package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties;
import com.danielfrak.code.keycloak.providers.rest.MigrationConfiguration;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.logging.Logger;
import org.keycloak.common.util.Encode;
import org.keycloak.component.ComponentModel;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class RestUserService implements LegacyUserService {

    private static final Logger LOG = Logger.getLogger(RestUserService.class);

    private final String providerName;
    private final String uriString;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RestUserService(ComponentModel model, HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerName = model.getName();
        this.uriString = model.getConfig().getFirst(ConfigurationProperties.URI_PROPERTY);
        this.objectMapper = objectMapper;

        configureBasicAuth(model, httpClient);
        configureBearerTokenAuth(model, httpClient);
        configureTimeouts(model, httpClient);
    }

    private void configureBasicAuth(ComponentModel model, HttpClient httpClient) {
        var basicAuthConfig = model.getConfig().getFirst(ConfigurationProperties.API_HTTP_BASIC_ENABLED_PROPERTY);
        var basicAuthEnabled = Boolean.parseBoolean(basicAuthConfig);
        if (basicAuthEnabled) {
            String basicAuthUser = model.getConfig().getFirst(
                    ConfigurationProperties.API_HTTP_BASIC_USERNAME_PROPERTY);
            String basicAuthPassword = model.getConfig().getFirst(
                    ConfigurationProperties.API_HTTP_BASIC_PASSWORD_PROPERTY);
            httpClient.enableBasicAuth(basicAuthUser, basicAuthPassword);
        }
    }

    private void configureBearerTokenAuth(ComponentModel model, HttpClient httpClient) {
        var tokenAuthEnabled = Boolean.parseBoolean(
                model.getConfig().getFirst(ConfigurationProperties.API_TOKEN_ENABLED_PROPERTY));
        if (tokenAuthEnabled) {
            String token = model.getConfig().getFirst(ConfigurationProperties.API_TOKEN_PROPERTY);
            httpClient.enableBearerTokenAuth(token);
        }
    }

    private void configureTimeouts(ComponentModel model, HttpClient httpClient) {
        var config = new MigrationConfiguration(model);
        config.getHttpConnectTimeoutMillis().ifPresent(httpClient::setConnectTimeout);
        config.getHttpReadTimeoutMillis().ifPresent(httpClient::setReadTimeout);
    }

    @Override
    public Optional<LegacyUser> findByEmail(String email) {
        return findLegacyUser(email)
                .filter(u -> equalsCaseInsensitive(email, u.email()));
    }

    private boolean equalsCaseInsensitive(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        return a.toUpperCase(Locale.ROOT).equals(b.toUpperCase(Locale.ROOT));
    }

    @Override
    public Optional<LegacyUser> findByUsername(String username) {
        return findLegacyUser(username)
                .filter(u -> equalsCaseInsensitive(username, u.username()));
    }

    private Optional<LegacyUser> findLegacyUser(String usernameOrEmail) {
        return Optional.ofNullable(usernameOrEmail)
                .flatMap(this::fetchLegacyUser);
    }

    private Optional<LegacyUser> fetchLegacyUser(String usernameOrEmail) {
        Optional<LegacyUser> legacyUser = Optional.empty();

        try {
            var response = this.httpClient.get(uriWithPathSegment(usernameOrEmail));

            if (response.getCode() == HttpStatus.SC_OK) {
                legacyUser = Optional.ofNullable(
                        objectMapper.readValue(response.getBody(), LegacyUser.class));
            }
        } catch (RuntimeException | IOException | URISyntaxException e) {
            LOG.error(lookupFailureMessage(e));
            LOG.debugf("Failed lookup was for user %s", usernameOrEmail);
        }

        return legacyUser;
    }

    private URI uriWithPathSegment(String pathSegment) throws URISyntaxException {
        var uriBuilder = new URIBuilder(this.uriString);

        return uriBuilder
                .setPathSegments(pathSegments(uriBuilder, pathSegment))
                .build();
    }

    private static List<String> pathSegments(URIBuilder uriBuilder, String... additionalPathSegments) {
        List<String> pathSegments = new ArrayList<>(uriBuilder.getPathSegments());
        pathSegments.addAll(Arrays.asList(additionalPathSegments));

        return pathSegments;
    }

    @Override
    public boolean isPasswordValid(String username, String password) {
        if (username != null) {
            username = Encode.urlEncode(username);
        }
        var dto = new UserPasswordDto(password);
        try {
            URI passwordValidationUri = uriWithPathSegment(username);
            var json = objectMapper.writeValueAsString(dto);
            var response = httpClient.post(passwordValidationUri, json);
            return response.getCode() == HttpStatus.SC_OK;
        } catch (IOException | URISyntaxException e) {
            LOG.error(passwordValidationFailureMessage(e, "treating password as invalid"));
            LOG.debugf("Failed password validation was for user %s", username);
            return false;
        } catch (RuntimeException e) {
            LOG.error(passwordValidationFailureMessage(e, "aborting password validation"));
            LOG.debugf("Failed password validation was for user %s", username);
            throw e;
        }
    }

    private String lookupFailureMessage(Exception e) {
        return failureMessage("user lookup", e, "treating user as not found");
    }

    private String passwordValidationFailureMessage(Exception e, String consequence) {
        return failureMessage("password validation", e, consequence);
    }

    private String failureMessage(String operation, Exception e, String consequence) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String causeDescription = cause.getClass().getSimpleName();
        if (cause.getMessage() != null) {
            causeDescription += ": " + cause.getMessage();
        }
        return String.format("Legacy %s failed for provider '%s' (%s): %s - %s",
                operation, providerName, uriString, causeDescription, consequence);
    }
}
