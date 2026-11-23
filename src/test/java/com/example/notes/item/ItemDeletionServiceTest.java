package com.example.notes.item;

import com.example.notes.item.permission.ItemAccessPolicy;
import com.example.notes.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemDeletionServiceTest {

    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemAccessPolicy access = mock(ItemAccessPolicy.class);
    private final ItemService service = new ItemService(items, mock(UserRepository.class), access);
    private final UUID callerId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final Item item = mock(Item.class);

    @Test
    void checksOwnershipOfTheLockedItemBeforeDeleting() {
        when(items.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        service.delete(callerId, itemId);

        var order = inOrder(items, access);
        order.verify(items).findByIdForUpdate(itemId);
        order.verify(access).requireOwner(item, callerId);
        order.verify(items).delete(item);
    }

    @Test
    void deniedOwnershipLeavesTheItemIntact() {
        when(items.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));
        doThrow(new AccessDeniedException("Owner required")).when(access).requireOwner(item, callerId);

        assertThatThrownBy(() -> service.delete(callerId, itemId)).isInstanceOf(AccessDeniedException.class);

        verify(items, never()).delete(any());
    }

    @Test
    void missingActiveItemIsNotFound() {
        when(items.findByIdForUpdate(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(callerId, itemId)).isInstanceOf(ItemNotFoundException.class);

        verifyNoInteractions(access);
        verify(items, never()).delete(any());
    }
}
