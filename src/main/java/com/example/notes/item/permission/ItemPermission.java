package com.example.notes.item.permission;

import com.example.notes.item.Item;
import com.example.notes.user.User;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "item_permissions")
public class ItemPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionRole role;

    protected ItemPermission() {
    }

    public ItemPermission(Item item, User user, PermissionRole role) {
        this.item = Objects.requireNonNull(item);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
    }

    public UUID getId() {
        return id;
    }

    public PermissionRole getRole() {
        return role;
    }

    public void changeRole(PermissionRole role) {
        this.role = Objects.requireNonNull(role);
    }
}