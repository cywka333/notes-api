package com.example.notes.item.permission;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record ShareItemRequest(@NotNull UUID userId, @NotNull PermissionRole role) {
}
