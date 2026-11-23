package com.example.notes.item;

import com.example.notes.item.permission.ItemAccessPolicy;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
class ItemEditingService {

    private final ItemRepository items;
    private final ItemAccessPolicy access;
    private final TransactionTemplate transactions;

    ItemEditingService(ItemRepository items, ItemAccessPolicy access, PlatformTransactionManager transactionManager) {
        this.items = items;
        this.access = access;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    UpdatedItem edit(UUID itemId, UUID callerId, PatchItemRequest patch) {
        try {
            return transactions.execute(status -> {
                Item item = editableItem(itemId, callerId);
                if (item.getVersion() != patch.version()) {
                    throw new ItemVersionConflictException(item.getVersion(), null);
                }
                patch.applyTo(item);
                items.flush();
                return UpdatedItem.from(item);
            });
        } catch (OptimisticLockingFailureException | OptimisticLockException conflict) {
            int version = transactions.execute(status -> editableItem(itemId, callerId).getVersion());
            throw new ItemVersionConflictException(version, conflict);
        }
    }

    private Item editableItem(UUID itemId, UUID callerId) {
        Item item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
        access.requireEdit(item, callerId);
        return item;
    }
}
