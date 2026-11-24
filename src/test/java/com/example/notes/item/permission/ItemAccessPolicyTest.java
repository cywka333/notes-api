package com.example.notes.item.permission;

import com.example.notes.item.model.Item;
import com.example.notes.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemAccessPolicyTest {

    private final ItemPermissionRepository permissions = mock(ItemPermissionRepository.class);
    private final ItemAccessPolicy access = new ItemAccessPolicy(permissions);
    private final User owner = mock(User.class);
    private final Item item = mock(Item.class);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUpItem() {
        when(owner.getId()).thenReturn(ownerId);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(itemId);
    }

    @Test
    void ownerCanReadEditAndManageWithoutAPermissionRow() {
        access.requireRead(item, ownerId);
        access.requireEdit(item, ownerId);
        access.requireOwner(item, ownerId);
        verifyNoInteractions(permissions);
    }

    @Test
    void editorCanReadAndEdit() {
        grant(PermissionRole.EDITOR);
        access.requireRead(item, callerId);
        access.requireEdit(item, callerId);
        assertThatThrownBy(() -> access.requireOwner(item, callerId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void viewerCanOnlyRead() {
        grant(PermissionRole.VIEWER);
        access.requireRead(item, callerId);
        assertThatThrownBy(() -> access.requireEdit(item, callerId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireOwner(item, callerId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void strangerIsDeniedEveryOperation() {
        assertThatThrownBy(() -> access.requireRead(item, callerId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireEdit(item, callerId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireOwner(item, callerId)).isInstanceOf(AccessDeniedException.class);
    }

    private void grant(PermissionRole role) {
        var permission = new ItemPermission(item, mock(User.class), role);
        when(permissions.findByItemIdAndUserId(itemId, callerId)).thenReturn(Optional.of(permission));
    }
}
