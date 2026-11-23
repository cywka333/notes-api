package com.example.notes.item.permission;

import com.example.notes.item.Item;
import com.example.notes.item.ItemRepository;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ItemSharingServiceTest {

    private final ItemRepository items = mock(ItemRepository.class);
    private final ItemPermissionRepository permissions = mock(ItemPermissionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ItemSharingService service = new ItemSharingService(items, permissions, users,
            new ItemAccessPolicy(permissions));
    private final UUID itemId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final User owner = mock(User.class);
    private final User recipient = new User("recipient", "unused");
    private final Item item = new Item(owner, "Title", null);

    @BeforeEach
    void setUpItem() {
        when(owner.getId()).thenReturn(ownerId);
        when(items.findForPermissionChange(itemId)).thenReturn(Optional.of(item));
    }

    @Test
    void ownerCannotShareWithThemself() {
        assertThatThrownBy(() -> service.share(itemId, ownerId, ownerId, PermissionRole.EDITOR))
                .isInstanceOf(SelfSharingException.class);
        verifyNoInteractions(permissions, users);
    }

    @Test
    void onlyOwnerCanManageGrants() {
        assertThatThrownBy(() -> service.share(itemId, UUID.randomUUID(), recipientId, PermissionRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(permissions, users);
    }

    @Test
    void firstGrantCreatesAPermission() {
        when(users.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(permissions.save(any(ItemPermission.class))).thenAnswer(returnsFirstArg());

        var result = service.share(itemId, ownerId, recipientId, PermissionRole.VIEWER);

        assertThat(result.created()).isTrue();
        assertThat(result.permission().itemId()).isEqualTo(itemId);
        assertThat(result.permission().userId()).isEqualTo(recipientId);
        assertThat(result.permission().role()).isEqualTo(PermissionRole.VIEWER);
        assertThat(result.permission().grantedAt()).isNotNull();
        verify(permissions).save(any(ItemPermission.class));
    }

    @ParameterizedTest
    @EnumSource(PermissionRole.class)
    void ownerCanOverwriteAnExistingRoleWithoutCreatingANewGrant(PermissionRole initial) {
        PermissionRole replacement = initial == PermissionRole.VIEWER ? PermissionRole.EDITOR : PermissionRole.VIEWER;
        var grant = new ItemPermission(item, recipient, initial);
        var grantedAt = grant.getGrantedAt();
        when(permissions.findByItemIdAndUserId(itemId, recipientId)).thenReturn(Optional.of(grant));

        var result = service.share(itemId, ownerId, recipientId, replacement);

        assertThat(result.created()).isFalse();
        assertThat(grant.getRole()).isEqualTo(replacement);
        assertThat(result.permission()).isEqualTo(new GrantedPermission(itemId, recipientId, replacement, grantedAt));
        verify(permissions, never()).save(any());
    }

    @Test
    void missingRecipientDoesNotCreateAPermission() {
        when(users.findById(recipientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.share(itemId, ownerId, recipientId, PermissionRole.VIEWER))
                .isInstanceOf(SharingTargetNotFoundException.class);
        verify(permissions, never()).save(any());
    }
}
