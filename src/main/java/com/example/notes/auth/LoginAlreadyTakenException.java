package com.example.notes.auth;

class LoginAlreadyTakenException extends RuntimeException {

    LoginAlreadyTakenException(Throwable cause) {
        super("Login is already taken", cause);
    }
}
