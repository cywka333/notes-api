package com.example.notes.item;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends Repository<Item, UUID> {
    Item save(Item item);

    Optional<Item> findById(UUID id);

    List<Item> findAllByOwnerId(UUID ownerId);

    @Query("""
            select new com.example.notes.item.ListedItem(
                i.id, i.title, i.content, i.version, i.owner.id,
                case when i.owner.id = :callerId then 'OWNER' else cast(p.role as string) end,
                i.updatedAt)
            from Item i
            left join ItemPermission p on p.item = i and p.user.id = :callerId
            where i.owner.id = :callerId or p.id is not null
            """)
    List<ListedItem> findVisibleTo(@Param("callerId") UUID callerId);

    void delete(Item item);
}
