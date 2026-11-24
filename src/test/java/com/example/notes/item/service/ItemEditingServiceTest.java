package com.example.notes.item.service;

import com.example.notes.item.model.Item;
import com.example.notes.item.permission.ItemAccessPolicy;
import com.example.notes.item.permission.ItemPermission;
import com.example.notes.item.permission.ItemPermissionRepository;
import com.example.notes.item.permission.PermissionRole;
import com.example.notes.item.persistence.ItemRepository;
import com.example.notes.user.User;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemEditingServiceTest {

    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemPermissionRepository permissions = mock(ItemPermissionRepository.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ItemEditingService editing = new ItemEditingService(items, new ItemAccessPolicy(permissions), manager);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private final User owner = mock(User.class);
    private final Item item = new Item(owner, "Original", "Keep this");

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(owner.getId()).thenReturn(ownerId);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "version", 4);
        when(items.findById(id)).thenReturn(Optional.of(item));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "EDITOR", "VIEWER", "STRANGER"})
    void appliesTheEditPolicyBeforeWriting(String role) {
        UUID caller = role.equals("OWNER") ? ownerId : otherId;
        if (role.equals("VIEWER") || role.equals("EDITOR")) {
            var grant = new ItemPermission(item, mock(User.class), PermissionRole.valueOf(role));
            when(permissions.findByItemIdAndUserId(id, caller)).thenReturn(Optional.of(grant));
        }

        if (role.equals("OWNER") || role.equals("EDITOR")) {
            UpdatedItem result = editing.edit(id, caller, changeTitle(4));
            assertThat(result.title()).isEqualTo("Changed");
            assertThat(result.content()).isEqualTo("Keep this");
            verify(items).flush();
        } else {
            assertThatThrownBy(() -> editing.edit(id, caller, changeTitle(4))).isInstanceOf(AccessDeniedException.class);
            assertThat(item.getTitle()).isEqualTo("Original");
            verify(items, never()).flush();
        }
    }

    @Test
    void clientWithAnOldVersionGetsTheCurrentVersionBeforeAnyWrite() {
        assertThatExceptionOfType(ItemVersionConflictException.class)
                .isThrownBy(() -> editing.edit(id, ownerId, changeTitle(3)))
                .satisfies(conflict -> assertThat(conflict.currentVersion()).isEqualTo(4));
        verify(items, never()).flush();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anOptimisticLockFailureRollsBackBeforeReadingTheCommittedVersion(boolean springTranslated) {
        RuntimeException failure = springTranslated
                ? new OptimisticLockingFailureException("Concurrent write")
                : new OptimisticLockException("Concurrent write");
        doThrow(failure).when(items).flush();
        Item committed = new Item(owner, "Another writer", null);
        ReflectionTestUtils.setField(committed, "version", 5);
        when(items.findById(id)).thenReturn(Optional.of(item)).thenReturn(Optional.of(committed));

        assertThatExceptionOfType(ItemVersionConflictException.class)
                .isThrownBy(() -> editing.edit(id, ownerId, changeTitle(4)))
                .satisfies(conflict -> {
                    assertThat(conflict.getCause()).isSameAs(failure);
                    assertThat(conflict.currentVersion()).isEqualTo(5);
                });

        var order = inOrder(items, manager);
        order.verify(items).flush();
        order.verify(manager).rollback(any());
        order.verify(items).findById(id);
    }

    private EditItemCommand changeTitle(int expectedVersion) {
        return new EditItemCommand(expectedVersion, "Changed", false, null);
    }
}
