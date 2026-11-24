package com.example.notes.item.permission;

import com.example.notes.item.model.Item;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ItemAccessPolicy {

    private final ItemPermissionRepository permissions;

    public ItemAccessPolicy(ItemPermissionRepository permissions) {
        this.permissions = permissions;
    }

    public void requireRead(Item item, UUID callerId) {
        if (!isOwner(item, callerId) && permissions.findByItemIdAndUserId(item.getId(), callerId).isEmpty()) {
            throw new AccessDeniedException("Read access is required for this item.");
        }
    }

    public void requireEdit(Item item, UUID callerId) {
        if (isOwner(item, callerId)) {
            return;
        }
        permissions.findByItemIdAndUserId(item.getId(), callerId)
                .filter(permission -> permission.getRole() == PermissionRole.EDITOR)
                .orElseThrow(() -> new AccessDeniedException("Edit access is required for this item."));
    }

    public void requireOwner(Item item, UUID callerId) {
        if (!isOwner(item, callerId)) {
            throw new AccessDeniedException("Only the item owner can perform this operation.");
        }
    }

    private static boolean isOwner(Item item, UUID callerId) {
        return item.getOwner().getId().equals(callerId);
    }
}
