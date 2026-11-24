package com.example.notes;

import com.example.notes.support.MySqlTestConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MySqlTestConfiguration.class)
class MySqlConnectionIT {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void connectsToTheTestDatabaseThroughJpa() {
        Object databaseName = entityManager.createNativeQuery("select database()")
                .getSingleResult();

        assertThat(databaseName).isEqualTo("notes_test");
    }
}