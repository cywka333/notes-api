package com.example.notes.auth.web;

import com.example.notes.auth.security.IssuedAccessToken;
import com.example.notes.auth.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LoginController {

    private final LoginService loginService;

    LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<IssuedAccessToken> login(@Valid @RequestBody LoginRequest request) {
        IssuedAccessToken response = loginService.login(request.login(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
