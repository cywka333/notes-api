package com.example.notes.item;

import com.example.notes.item.model.Item;
import com.example.notes.item.permission.ItemAccessPolicy;
import com.example.notes.item.permission.ItemPermission;
import com.example.notes.item.permission.ItemPermissionRepository;
import com.example.notes.item.permission.PermissionRole;
import com.example.notes.item.persistence.ItemRepository;
import com.example.notes.item.service.ItemNotFoundException;
import com.example.notes.user.User;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemHistoryServiceTest {

    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemPermissionRepository permissions = mock(ItemPermissionRepository.class);
    private final ItemHistoryRepository history = mock(ItemHistoryRepository.class);
    private final ItemHistoryService service = new ItemHistoryService(items, new ItemAccessPolicy(permissions), history);
    private final UUID itemId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID readerId = UUID.randomUUID();
    private final Item item = mock(Item.class);
    private final User owner = mock(User.class);

    @BeforeEach
    void setUpItem() {
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(item.getId()).thenReturn(itemId);
        when(item.getOwner()).thenReturn(owner);
        when(owner.getId()).thenReturn(ownerId);
    }

    @Test
    void ownerCanReadAnEmptyHistory() {
        when(history.findRevisions(itemId)).thenReturn(List.of());
        assertThat(service.getHistory(itemId, ownerId)).isEmpty();
        verifyNoInteractions(permissions);
    }

    @ParameterizedTest
    @EnumSource(PermissionRole.class)
    void sharedReadersReceiveTheNotesRevisions(PermissionRole role) {
        var grant = new ItemPermission(item, mock(User.class), role);
        when(permissions.findByItemIdAndUserId(itemId, readerId)).thenReturn(Optional.of(grant));
        var entry = new ItemHistoryEntry(17, RevisionType.ADD, Instant.EPOCH, "author", "Original", null);
        when(history.findRevisions(itemId)).thenReturn(List.of(entry));

        assertThat(service.getHistory(itemId, readerId)).containsExactly(entry);
    }

    @Test
    void aCallerWithoutACurrentGrantCannotReadPastRevisions() {
        when(permissions.findByItemIdAndUserId(itemId, readerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(itemId, readerId)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(history);
    }

    @Test
    void missingActiveItemBlocksAuditAccessEvenForItsFormerOwner() {
        when(items.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(itemId, ownerId)).isInstanceOf(ItemNotFoundException.class);
        verifyNoInteractions(permissions, history);
    }
}
