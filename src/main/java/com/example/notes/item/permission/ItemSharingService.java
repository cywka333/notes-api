package com.example.notes.item.permission;

import com.example.notes.item.model.Item;
import com.example.notes.item.persistence.ItemRepository;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ItemSharingService {

    private final ItemRepository items;
    private final ItemPermissionRepository permissions;
    private final UserRepository users;
    private final ItemAccessPolicy access;

    ItemSharingService(ItemRepository items, ItemPermissionRepository permissions, UserRepository users,
                       ItemAccessPolicy access) {
        this.items = items;
        this.permissions = permissions;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public Result share(UUID itemId, UUID callerId, UUID recipientId, PermissionRole role) {
        Item item = requireOwnedItemForPermissionChange(itemId, callerId);
        if (callerId.equals(recipientId)) {
            throw new SelfSharingException();
        }

        Optional<ItemPermission> existing = permissions.findByItemIdAndUserId(itemId, recipientId);
        ItemPermission permission;
        if (existing.isPresent()) {
            permission = existing.get();
            permission.changeRole(role);
        } else {
            User recipient = users.findById(recipientId)
                    .orElseThrow(() -> new SharingTargetNotFoundException("Recipient not found."));
            permission = permissions.save(new ItemPermission(item, recipient, role));
        }
        return new Result(new GrantedPermission(itemId, recipientId, permission.getRole(), permission.getGrantedAt()),
                existing.isEmpty());
    }

    @Transactional
    public void revoke(UUID itemId, UUID callerId, UUID recipientId) {
        requireOwnedItemForPermissionChange(itemId, callerId);
        if (permissions.deleteGrant(itemId, recipientId) == 0) {
            throw new SharingTargetNotFoundException("Permission not found.");
        }
    }

    private Item requireOwnedItemForPermissionChange(UUID itemId, UUID callerId) {
        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new SharingTargetNotFoundException("Item not found."));
        access.requireOwner(item, callerId);
        return item;
    }

    public record Result(GrantedPermission permission, boolean created) {
    }
}
