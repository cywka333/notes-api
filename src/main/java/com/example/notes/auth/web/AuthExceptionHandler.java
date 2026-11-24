package com.example.notes.auth.web;

import com.example.notes.auth.service.LoginAlreadyTakenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = LoginController.class)
class AuthExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid login or password format.");
    }

    @ExceptionHandler(LoginAlreadyTakenException.class)
    ProblemDetail loginAlreadyTaken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Login is already taken.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail invalidCredentials(BadCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
}
