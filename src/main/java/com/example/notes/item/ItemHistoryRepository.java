package com.example.notes.item;

import com.example.notes.item.model.Item;
import com.example.notes.audit.ItemRevision;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class ItemHistoryRepository {

    private final EntityManager entityManager;

    ItemHistoryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    List<ItemHistoryEntry> findRevisions(UUID itemId) {
        List<?> rows = AuditReaderFactory.get(entityManager).createQuery()
                .forRevisionsOfEntity(Item.class, false, true)
                .add(AuditEntity.id().eq(itemId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(Object[].class::cast).map(ItemHistoryRepository::toEntry).toList();
    }

    private static ItemHistoryEntry toEntry(Object[] row) {
        Item snapshot = (Item) row[0];
        ItemRevision revision = (ItemRevision) row[1];
        RevisionType type = (RevisionType) row[2];
        return new ItemHistoryEntry(revision.getId(), type, Instant.ofEpochMilli(revision.getTimestamp()),
                revision.getChangedBy(), snapshot.getTitle(), snapshot.getContent());
    }
}
