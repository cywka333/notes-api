package com.example.notes.user;

import com.example.notes.support.MySqlTestConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
public class UserRepositoryIT {

    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private UserRepository users;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void readsTheStoredUserByLogin() {
        User saved = users.saveAndFlush(new User("jakub.cywinski", PASSWORD_HASH));
        entityManager.clear();

        User found = users.findByLogin("Jakub.Cywinski").orElseThrow();

        assertThat(found.getId()).isNotNull().isEqualTo(saved.getId());
        assertThat(found.getLogin()).isEqualTo("jakub.cywinski");
        assertThat(found.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(found.getCreatedAt()).isNotNull().isEqualTo(saved.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jakub.cywinski", "Jakub.Cywinski"})
    void rejectDuplicateLoginsInTheDatabase(String duplicateLogin) {
        users.saveAndFlush(new User("jakub.cywinski", PASSWORD_HASH));

        assertThatThrownBy(() -> users.saveAndFlush(new User(duplicateLogin, PASSWORD_HASH)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                .hasMessageContaining("uk_users_login");
    }
}
