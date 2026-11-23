package com.example.notes.item;

import com.example.notes.item.permission.ItemAccessPolicy;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
class ItemService {

    private static final Comparator<ListedItem> LIST_ORDER = Comparator.comparing(ListedItem::updatedAt)
            .reversed()
            .thenComparing(item -> item.id().toString());

    private final ItemRepository items;
    private final UserRepository users;
    private final ItemAccessPolicy access;

    ItemService(ItemRepository items, UserRepository users, ItemAccessPolicy access) {
        this.items = items;
        this.users = users;
        this.access = access;
    }

    @Transactional
    CreatedItem create(UUID callerId, String title, String content) {
        User owner = users.findById(callerId)
                .orElseThrow(() -> new InvalidBearerTokenException("Unknown token subject."));
        return CreatedItem.from(items.save(new Item(owner, title, content)));
    }

    @Transactional(readOnly = true)
    List<ListedItem> list(UUID callerId) {
        return items.findVisibleTo(callerId).stream().sorted(LIST_ORDER).toList();
    }

    @Transactional
    void delete(UUID callerId, UUID itemId) {
        Item item = items.findByIdForUpdate(itemId).orElseThrow(ItemNotFoundException::new);
        access.requireOwner(item, callerId);
        items.delete(item);
    }
}
