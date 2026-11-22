package com.example.notes.auth;

import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String login, String rawPassword) {
        User user = new User(login, passwordEncoder.encode(rawPassword));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if ("uk_users_login".equals(constraint) || "users.uk_users_login".equals(constraint)) {
                    throw new LoginAlreadyTakenException(exception);
                }
            }
            throw exception;
        }
    }
}
