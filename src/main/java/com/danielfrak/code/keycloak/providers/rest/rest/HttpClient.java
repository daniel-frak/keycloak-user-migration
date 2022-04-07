package com.danielfrak.code.keycloak.providers.rest.rest;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpClient {
    private static final String BEARER_FORMAT = "Bearer %s";
    private static final String BASIC_AUTH_FORMAT = "Basic %s";
    private static final String USERNAME_PASSWORD_FORMAT = "%s:%s";

    private final HttpClientBuilder httpClientBuilder;
    public HttpClient() {
       this.httpClientBuilder = HttpClientBuilder.create();
    }

    public void enableBasicAuth(String basicAuthUser, String basicAuthPassword) {
        if (basicAuthUser != null
                && !basicAuthUser.isBlank()
                && basicAuthPassword != null
                && !basicAuthPassword.isBlank()) {
            String auth = String.format(USERNAME_PASSWORD_FORMAT, basicAuthUser, basicAuthPassword);
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
            var authorizationHeader = new BasicHeader(HttpHeaders.AUTHORIZATION,
                    String.format(BASIC_AUTH_FORMAT, new String(encodedAuth, StandardCharsets.ISO_8859_1)));
            httpClientBuilder.setDefaultHeaders(List.of(authorizationHeader));
        }
    }

    public void enableBearerTokenAuth(String token) {
        if (token != null && !token.isBlank()) {
            var authorizationHeader = new BasicHeader(HttpHeaders.AUTHORIZATION, String.format(BEARER_FORMAT, token));
            httpClientBuilder.setDefaultHeaders(List.of(authorizationHeader));
        }
    }

    public Response get(String uri) {
        var request = new HttpGet(uri);
        return execute(request);
    }

    private Response execute(HttpUriRequest request) {
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        try(
                var build = httpClientBuilder.build();
                var response = build.execute(request)
        ) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                return new Response(statusCode);
            }
            var entity = response.getEntity();
            var encodingHeader = entity.getContentEncoding();
            var encoding = encodingHeader == null ?
                    StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            var entityAsString = EntityUtils.toString(entity, encoding);
            return new Response(statusCode, entityAsString);
        } catch (IOException e) {
            throw new RestUserProviderException(e);
        }
    }

    public Response post(String uri, String bodyAsJson) {
        var request = new HttpPost(uri);
        var requestEntity = new StringEntity(bodyAsJson, ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);
        return execute(request);
    }
}