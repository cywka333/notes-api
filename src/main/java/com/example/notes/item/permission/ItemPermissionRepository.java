package com.example.notes.item.permission;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ItemPermissionRepository extends Repository<ItemPermission, UUID> {

    ItemPermission save(ItemPermission permission);

    Optional<ItemPermission> findByItemIdAndUserId(UUID itemId, UUID userId);
}
