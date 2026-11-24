package com.example.notes.auth.service;

public class LoginAlreadyTakenException extends RuntimeException {

    LoginAlreadyTakenException(Throwable cause) {
        super("Login is already taken", cause);
    }
}
