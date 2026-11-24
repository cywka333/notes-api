package com.example.notes.item;

import com.example.notes.item.permission.ItemAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class ItemHistoryService {

    private final ItemRepository items;
    private final ItemAccessPolicy access;
    private final ItemHistoryRepository history;

    ItemHistoryService(ItemRepository items, ItemAccessPolicy access, ItemHistoryRepository history) {
        this.items = items;
        this.access = access;
        this.history = history;
    }

    @Transactional(readOnly = true)
    List<ItemHistoryEntry> getHistory(UUID itemId, UUID callerId) {
        Item item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
        access.requireRead(item, callerId);
        return history.findRevisions(itemId);
    }
}
