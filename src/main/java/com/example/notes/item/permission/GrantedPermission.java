package com.example.notes.item.permission;

import java.time.Instant;
import java.util.UUID;

public record GrantedPermission(UUID itemId, UUID userId, PermissionRole role, Instant grantedAt) {
}
