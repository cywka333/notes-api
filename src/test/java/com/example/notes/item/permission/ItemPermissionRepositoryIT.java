package com.example.notes.item.permission;

import com.example.notes.item.model.Item;
import com.example.notes.support.MySqlTestConfiguration;
import com.example.notes.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MySqlTestConfiguration.class, ItemAccessPolicy.class})
class ItemPermissionRepositoryIT {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private ItemPermissionRepository permissions;
    @Autowired
    private ItemAccessPolicy access;

    @Test
    void grantsAreScopedToBothItemAndUserAndRoleChangesApplyImmediately() {
        User owner = user("owner");
        User editor = user("editor");
        User viewer = user("viewer");
        Item first = item(owner, "First");
        Item second = item(owner, "Second");
        ItemPermission grant = permissions.save(new ItemPermission(first, editor, PermissionRole.EDITOR));
        permissions.save(new ItemPermission(first, viewer, PermissionRole.VIEWER));
        permissions.save(new ItemPermission(second, editor, PermissionRole.VIEWER));
        flushAndClear();

        Item firstItem = em.find(Item.class, first.getId());
        Item secondItem = em.find(Item.class, second.getId());
        access.requireEdit(firstItem, editor.getId());
        access.requireRead(firstItem, viewer.getId());
        access.requireRead(secondItem, editor.getId());
        assertThatThrownBy(() -> access.requireEdit(secondItem, editor.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireRead(secondItem, viewer.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireEdit(firstItem, viewer.getId())).isInstanceOf(AccessDeniedException.class);

        permissions.findByItemIdAndUserId(first.getId(), editor.getId()).orElseThrow().changeRole(PermissionRole.VIEWER);
        flushAndClear();

        var updated = permissions.findByItemIdAndUserId(first.getId(), editor.getId()).orElseThrow();
        assertThat(updated.getId()).isEqualTo(grant.getId());
        assertThat(updated.getRole()).isEqualTo(PermissionRole.VIEWER);
        assertThatThrownBy(() -> access.requireEdit(firstItem, editor.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsASecondGrantForTheSameItemAndUser() {
        User owner = user("owner");
        User recipient = user("recipient");
        Item note = item(owner, "Note");
        permissions.save(new ItemPermission(note, recipient, PermissionRole.VIEWER));
        em.flush();

        permissions.save(new ItemPermission(note, recipient, PermissionRole.EDITOR));
        assertThatThrownBy(em::flush).isInstanceOf(ConstraintViolationException.class)
                .rootCause().hasMessageContaining("uk_item_permissions_item_user");
    }

    @ParameterizedTest
    @ValueSource(strings = {"item", "user"})
    void enforcesBothForeignKeys(String missing) {
        User owner = user("owner");
        Item note = item(owner, "Note");
        User recipient = user("recipient");
        em.flush();
        if (missing.equals("item")) {
            note = em.getReference(Item.class, UUID.randomUUID());
        } else {
            recipient = em.getReference(User.class, UUID.randomUUID());
        }
        permissions.save(new ItemPermission(note, recipient, PermissionRole.VIEWER));
        assertThatThrownBy(em::flush).isInstanceOf(ConstraintViolationException.class)
                .rootCause().hasMessageContaining("fk_item_permissions_" + missing);
    }

    private User user(String login) {
        User user = new User(login, "unused-in-repository-test");
        em.persist(user);
        return user;
    }

    private Item item(User owner, String title) {
        Item item = new Item(owner, title, null);
        em.persist(item);
        return item;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
