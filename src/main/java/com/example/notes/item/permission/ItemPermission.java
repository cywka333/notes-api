package com.example.notes.item.permission;

import com.example.notes.item.model.Item;
import com.example.notes.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected ItemPermission() {
    }

    public ItemPermission(Item item, User user, PermissionRole role) {
        this.item = Objects.requireNonNull(item);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
        this.grantedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public PermissionRole getRole() {
        return role;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void changeRole(PermissionRole role) {
        this.role = Objects.requireNonNull(role);
    }
}
