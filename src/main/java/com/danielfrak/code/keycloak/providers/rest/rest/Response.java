package com.danielfrak.code.keycloak.providers.rest.rest;

class Response {
    int code;
    String body;

    public Response(int code, String body) {
        this.code = code;
        this.body = body;
    }

    public Response(int code) {
        this.code = code;
    }
}
