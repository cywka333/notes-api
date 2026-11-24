package com.example.notes.item.permission;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItemPermissionRepository extends Repository<ItemPermission, UUID> {

    ItemPermission save(ItemPermission permission);

    Optional<ItemPermission> findByItemIdAndUserId(UUID itemId, UUID userId);

    @Modifying
    @Query("delete from ItemPermission p where p.item.id = :itemId and p.user.id = :userId")
    int deleteGrant(@Param("itemId") UUID itemId, @Param("userId") UUID userId);
}
