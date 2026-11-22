package com.example.notes.auth;

record LoginResponse(String token, long expiresIn) {

    @Override
    public String toString() {
        return "LoginResponse[expiresIn=" + expiresIn + "]";
    }
}