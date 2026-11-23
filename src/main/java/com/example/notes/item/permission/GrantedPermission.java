package com.example.notes.item.permission;

import java.time.Instant;
import java.util.UUID;

record GrantedPermission(UUID itemId, UUID userId, PermissionRole role, Instant grantedAt) {
}
