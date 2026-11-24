package com.example.notes.item.web;

import com.example.notes.item.permission.PermissionRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record ShareItemRequest(@NotNull UUID userId, @NotNull PermissionRole role) {
}
