package com.example.notes.item;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends Repository<Item, UUID> {
    Item save(Item item);

    Optional<Item> findById(UUID id);

    List<Item> findAllByOwnerId(UUID ownerId);

    void delete(Item item);
}
