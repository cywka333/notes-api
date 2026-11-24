package com.example.notes.item.history;

import com.example.notes.item.model.Item;
import com.example.notes.item.persistence.ItemRepository;
import com.example.notes.support.MySqlTestConfiguration;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(MySqlTestConfiguration.class)
class ItemAuditPersistenceIT {

    @Autowired
    private UserRepository users;
    @Autowired
    private ItemRepository items;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ItemHistoryRepository historyRepository;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsTheActualActorAndKeepsTheSoftDeletedRow() {
        User owner = newUser("Owner");
        User editor = newUser("Editor");
        authenticate(owner);
        var tx = new TransactionTemplate(transactionManager);
        Item created = tx.execute(status -> items.save(new Item(owner, "First title", null)));
        UUID id = created.getId();
        assertThat(created.getVersion()).isZero();
        assertThat(created.getCreatedAt()).isNotNull().isEqualTo(created.getUpdatedAt());
        assertThat(items.findAllByOwnerId(owner.getId())).extracting(Item::getId).containsExactly(id);

        authenticate(editor);
        tx.executeWithoutResult(status -> {
            Item item = items.findById(id).orElseThrow();
            item.changeTitle("Edited title");
            item.changeContent("Edited content");
        });
        Item edited = items.findById(id).orElseThrow();
        assertThat(edited.getVersion()).isOne();
        assertThat(edited.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(edited.getUpdatedAt()).isAfterOrEqualTo(created.getUpdatedAt());

        authenticate(owner);
        tx.executeWithoutResult(status -> items.delete(items.findById(id).orElseThrow()));
        assertThat(items.findById(id)).isEmpty();
        assertThat(items.findAllByOwnerId(owner.getId())).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT version FROM items WHERE id = UUID_TO_BIN(?) AND deleted = true
                """, Integer.class, id.toString())).isEqualTo(2);

        List<ItemHistoryEntry> changes = history(id);
        assertThat(changes).extracting(ItemHistoryEntry::revision).isSorted().doesNotHaveDuplicates();
        assertThat(changes).extracting(ItemHistoryEntry::revisionType, ItemHistoryEntry::changedBy, ItemHistoryEntry::title, ItemHistoryEntry::content)
                .containsExactly(
                        tuple(RevisionType.ADD, owner.getLogin(), "First title", null),
                        tuple(RevisionType.MOD, editor.getLogin(), "Edited title", "Edited content"),
                        tuple(RevisionType.DEL, owner.getLogin(), "Edited title", "Edited content"));
    }

    @ParameterizedTest(name = "rejects a stale write (delete = {0})")
    @ValueSource(booleans = {false, true})
    void rejectsStaleUpdatesAndDeletesWithoutRecordingARevision(boolean delete) {
        User owner = newUser("Owner");
        authenticate(owner);
        var tx = new TransactionTemplate(transactionManager);
        var independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID id = tx.execute(status -> items.save(new Item(owner, "Original", null)).getId());

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            Item stale = items.findById(id).orElseThrow();
            independent.executeWithoutResult(other -> items.findById(id).orElseThrow().changeTitle("Winner"));
            if (delete) {
                items.delete(stale);
            } else {
                stale.changeTitle("Stale");
            }
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(items.findById(id).orElseThrow().getTitle()).isEqualTo("Winner");
        assertThat(history(id)).extracting(ItemHistoryEntry::revisionType).containsExactly(RevisionType.ADD, RevisionType.MOD);
    }

    @Test
    void rollsBackTheItemWhenTheActorIsMissing() {
        User owner = newUser("Owner");
        assertThatThrownBy(() -> items.save(new Item(owner, "Missing actor", null)))
                .hasRootCauseMessage("An authenticated user is required to audit an item change.");
        assertThat(items.findAllByOwnerId(owner.getId())).isEmpty();
    }

    private User newUser(String prefix) {
        return users.saveAndFlush(new User(prefix + "-" + UUID.randomUUID(), "unused-in-repository-test"));
    }

    private static void authenticate(User user) {
        Jwt jwt = Jwt.withTokenValue("repository-test").header("alg", "HS256")
                .subject(user.getId().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private List<ItemHistoryEntry> history(UUID id) {
        return new TransactionTemplate(transactionManager).execute(status -> historyRepository.findRevisions(id));
    }
}
