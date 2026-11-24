package com.example.notes.item.service;

import com.example.notes.item.model.Item;
import com.example.notes.item.permission.ItemAccessPolicy;
import com.example.notes.item.persistence.ItemRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class ItemEditingService {

    private final ItemRepository items;
    private final ItemAccessPolicy access;
    private final TransactionTemplate transactions;

    ItemEditingService(ItemRepository items, ItemAccessPolicy access, PlatformTransactionManager transactionManager) {
        this.items = items;
        this.access = access;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public UpdatedItem edit(UUID itemId, UUID callerId, EditItemCommand command) {
        try {
            return transactions.execute(status -> {
                Item item = editableItem(itemId, callerId);
                if (item.getVersion() != command.expectedVersion()) {
                    throw new ItemVersionConflictException(item.getVersion(), null);
                }
                if (command.title() != null) {
                    item.changeTitle(command.title());
                }
                if (command.contentPresent()) {
                    item.changeContent(command.content());
                }
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
