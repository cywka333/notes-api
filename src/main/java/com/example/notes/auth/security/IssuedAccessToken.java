package com.example.notes.auth.security;

public record IssuedAccessToken(String token, long expiresIn) {

    @Override
    public String toString() {
        return "IssuedAccessToken[expiresIn=" + expiresIn + "]";
    }
}
