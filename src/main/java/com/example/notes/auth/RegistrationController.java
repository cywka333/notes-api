package com.example.notes.auth;

import com.example.notes.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
class RegistrationController {

    private final RegistrationService registrationService;

    RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request.login(), request.password());
        return new RegisterResponse(user.getId(), user.getLogin(), user.getCreatedAt());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid login or password format.");
    }

    @ExceptionHandler(LoginAlreadyTakenException.class)
    ProblemDetail loginAlreadyTaken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Login is already taken.");
    }
}
