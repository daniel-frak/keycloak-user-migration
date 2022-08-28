package com.danielfrak.code.keycloak.providers.rest.rest.http;

public class HttpResponse {

    int code;
    String body;

    public HttpResponse(int code, String body) {
        this.code = code;
        this.body = body;
    }

    public HttpResponse(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return body;
    }
}
