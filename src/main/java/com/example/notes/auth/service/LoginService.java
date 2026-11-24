package com.example.notes.auth.service;

import com.example.notes.auth.security.AccessTokenService;
import com.example.notes.auth.security.IssuedAccessToken;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AccessTokenService tokens;
    private final String dummyHash;

    LoginService(UserRepository users, PasswordEncoder passwords, AccessTokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public IssuedAccessToken login(String login, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid login or password.");
        }
        Optional<User> account = users.findByLogin(login);
        String hash = account.map(User::getPasswordHash).orElse(dummyHash);
        if (!passwords.matches(password, hash) || account.isEmpty()) {
            throw new BadCredentialsException("Invalid login or password.");
        }
        return tokens.issue(account.orElseThrow().getId());
    }
}
